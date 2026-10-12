package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.config.ContextualRetrievalProperties;
import com.yizhaoqi.smartpai.config.KnowledgeIndex;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.model.EmbeddingIndexMigration;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.EmbeddingIndexMigrationRepository;
import com.yizhaoqi.smartpai.service.chunk.ContextualChunkText;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Builds a new vector index while searches keep using the old alias. */
@Service
public class EmbeddingIndexMigrationService {
    private static final int BATCH_SIZE = 64;
    private static final DateTimeFormatter VERSION_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String BUILDING = "BUILDING";
    private static final String READY = "READY";
    private static final String CATCHING_UP = "CATCHING_UP";
    private static final String SWITCHED = "SWITCHED";
    private static final String ROLLED_BACK = "ROLLED_BACK";
    private static final String ROLLING_BACK = "ROLLING_BACK";
    private static final String FAILED = "FAILED";

    private final ElasticsearchClient es;
    private final ElasticsearchService elasticsearchService;
    private final EmbeddingClient embeddingClient;
    private final ModelProviderConfigService providers;
    private final DocumentVectorRepository chunks;
    private final EmbeddingIndexMigrationRepository jobs;
    private final KafkaListenerEndpointRegistry listeners;
    private final ContextualRetrievalProperties contextual;
    private final ObjectMapper mapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "embedding-index-migration");
        thread.setDaemon(true);
        return thread;
    });

    @Value("classpath:es-mappings/knowledge_base.json")
    private Resource mapping;

    public EmbeddingIndexMigrationService(ElasticsearchClient es, ElasticsearchService elasticsearchService,
                                          EmbeddingClient embeddingClient, ModelProviderConfigService providers,
                                          DocumentVectorRepository chunks, EmbeddingIndexMigrationRepository jobs,
                                          KafkaListenerEndpointRegistry listeners, ContextualRetrievalProperties contextual,
                                          ObjectMapper mapper) {
        this.es = es;
        this.elasticsearchService = elasticsearchService;
        this.embeddingClient = embeddingClient;
        this.providers = providers;
        this.chunks = chunks;
        this.jobs = jobs;
        this.listeners = listeners;
        this.contextual = contextual;
        this.mapper = mapper;
    }

    @PostConstruct
    public void recoverInterruptedJob() {
        jobs.findById(1L).ifPresent(job -> {
            if (BUILDING.equals(job.getStatus()) || CATCHING_UP.equals(job.getStatus())) {
                job.setStatus(FAILED);
                job.setErrorMessage("服务在重建期间重启；旧索引仍在服务，请重新发起重建");
                save(job);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }

    /** Repair a crash between the ES alias update and the provider switch. */
    public synchronized void reconcileAfterStartup() throws Exception {
        EmbeddingIndexMigration job = status();
        if (job == null) {
            return;
        }
        String activeIndex = activePhysicalIndex();
        if (activeIndex.equals(job.getTargetIndex())) {
            var active = providers.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING);
            if (!active.provider().equals(job.getTargetProvider()) || !active.model().equals(job.getTargetModel())) {
                providers.activateRebuiltEmbeddingProvider(job.getTargetProvider(), job.getTargetModel(),
                        job.getTargetDimension(), "migration-recovery");
            }
            if (!SWITCHED.equals(job.getStatus())) {
                job.setStatus(SWITCHED);
                save(job);
            }
        } else if (activeIndex.equals(job.getPreviousIndex())) {
            var active = providers.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING);
            if (!active.provider().equals(job.getPreviousProvider()) || !active.model().equals(job.getPreviousModel())) {
                providers.activateRebuiltEmbeddingProvider(job.getPreviousProvider(), job.getPreviousModel(),
                        job.getPreviousDimension(), "migration-recovery");
            }
            if (ROLLING_BACK.equals(job.getStatus())) {
                job.setStatus(ROLLED_BACK);
                save(job);
            }
        } else {
            throw new IllegalStateException("知识库别名指向了未知索引：" + activeIndex);
        }
    }

    public synchronized EmbeddingIndexMigration status() {
        return jobs.findById(1L).orElse(null);
    }

    public synchronized EmbeddingIndexMigration start(String targetProvider, String requesterId) throws Exception {
        return start(targetProvider, null, null, requesterId);
    }

    public synchronized EmbeddingIndexMigration start(String targetProvider, String targetModel,
                                                       Integer targetDimension, String requesterId) throws Exception {
        EmbeddingIndexMigration previous = status();
        if (previous != null && (BUILDING.equals(previous.getStatus()) || CATCHING_UP.equals(previous.getStatus())
                || ROLLING_BACK.equals(previous.getStatus()))) {
            throw new IllegalStateException("已有 Embedding 重建任务正在运行");
        }
        var target = providers.getEmbeddingProvider(targetProvider);
        var current = providers.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING);
        String model = targetModel == null || targetModel.isBlank() ? target.model() : targetModel.trim();
        Integer dimension = targetDimension == null ? target.dimension() : targetDimension;
        if (dimension == null || dimension <= 0) {
            throw new IllegalArgumentException("目标 Embedding 维度必须大于 0");
        }
        if (target.provider().equals(current.provider()) && model.equals(current.model())
                && dimension.equals(current.dimension())) {
            throw new IllegalArgumentException("目标模型与当前模型相同，无需重建");
        }
        String index = "knowledge_base_v" + VERSION_TIME.format(LocalDateTime.now())
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode root = (ObjectNode) mapper.readTree(mapping.getInputStream());
        ((ObjectNode) root.path("mappings").path("properties").path("vector")).put("dims", dimension);
        String mappingJson = mapper.writeValueAsString(root);
        es.indices().create(c -> c.index(index).withJson(new StringReader(mappingJson)));

        EmbeddingIndexMigration job = new EmbeddingIndexMigration();
        job.setStatus(BUILDING);
        job.setTargetIndex(index);
        job.setTargetProvider(target.provider());
        job.setTargetModel(model);
        job.setTargetDimension(dimension);
        job.setPreviousIndex(activePhysicalIndex());
        job.setPreviousProvider(current.provider());
        job.setPreviousModel(current.model());
        job.setPreviousDimension(current.dimension());
        job.setExpectedChunks(chunks.countDistinctChunks());
        save(job);
        worker.execute(() -> build(job.getTargetIndex(), requesterId));
        return job;
    }

    private void build(String index, String requesterId) {
        try {
            EmbeddingIndexMigration job = jobs.findById(1L).orElseThrow();
            indexAllChunks(job, job.getTargetIndex(), job.getTargetProvider(), job.getTargetModel(),
                    job.getTargetDimension(), requesterId);
            es.indices().refresh(r -> r.index(index));
            long count = es.count(c -> c.index(index)).count();
            if (count != chunks.countDistinctChunks()) {
                throw new IllegalStateException("候选索引文档数不一致：ES=" + count + "，MySQL=" + chunks.countDistinctChunks());
            }
            job.setExpectedChunks(count);
            job.setStatus(READY);
            save(job);
        } catch (Exception e) {
            fail(e);
        }
    }

    /** The last pass runs while the Kafka listener is paused, then validates and switches. */
    public synchronized EmbeddingIndexMigration switchIndex(List<Probe> probes, String requesterId) {
        EmbeddingIndexMigration job = requiredJob(READY);
        if (probes == null || probes.isEmpty()) {
            throw new IllegalArgumentException("切换前至少提供一道带预期文件 MD5 的抽样问题");
        }
        job.setStatus(CATCHING_UP);
        job.setProcessedChunks(0);
        job.setErrorMessage(null);
        save(job);
        worker.execute(() -> catchUpAndSwitch(probes, requesterId));
        return job;
    }

    private void catchUpAndSwitch(List<Probe> probes, String requesterId) {
        MessageListenerContainer consumer = listeners.getListenerContainer("fileProcessing");
        try {
            if (consumer == null || !consumer.isRunning()) {
                throw new IllegalStateException("文件消费监听器未运行，无法确认追平阶段的写入边界");
            }
            consumer.pause();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!consumer.isContainerPaused() && System.nanoTime() < deadline) {
                Thread.sleep(200);
            }
            if (!consumer.isContainerPaused()) {
                throw new IllegalStateException("文件消费未能在 30 秒内暂停");
            }
            try (EmbeddingSwitchGate.Permit mutation = DocumentMutationGate.write()) {
            EmbeddingIndexMigration job = jobs.findById(1L).orElseThrow();
            job.setProcessedChunks(0);
            reconcileIndex(job, job.getTargetIndex(), job.getTargetProvider(), job.getTargetModel(),
                    job.getTargetDimension(), requesterId);
            es.indices().refresh(r -> r.index(job.getTargetIndex()));
            long expected = chunks.countDistinctChunks();
            long actual = es.count(c -> c.index(job.getTargetIndex())).count();
            if (actual != expected) {
                throw new IllegalStateException("追平后文档数不一致：ES=" + actual + "，MySQL=" + expected);
            }
            validateProbes(job, probes, requesterId);
            try (EmbeddingSwitchGate.Permit ignored = EmbeddingSwitchGate.write()) {
                String active = activePhysicalIndex();
                if (!active.equals(job.getPreviousIndex())) {
                    throw new IllegalStateException("当前别名已被其他任务切换");
                }
                es.indices().updateAliases(u -> u.actions(a -> a.remove(r -> r.index(active).alias(KnowledgeIndex.ALIAS)))
                        .actions(a -> a.add(add -> add.index(job.getTargetIndex()).alias(KnowledgeIndex.ALIAS))));
                try {
                    providers.activateRebuiltEmbeddingProvider(job.getTargetProvider(), job.getTargetModel(),
                            job.getTargetDimension(), requesterId);
                } catch (Exception e) {
                    es.indices().updateAliases(u -> u.actions(a -> a.remove(r -> r.index(job.getTargetIndex()).alias(KnowledgeIndex.ALIAS)))
                            .actions(a -> a.add(add -> add.index(active).alias(KnowledgeIndex.ALIAS))));
                    throw e;
                }
            }
            job.setExpectedChunks(expected);
            job.setStatus(SWITCHED);
            save(job);
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            if (consumer != null && consumer.isRunning()) {
                consumer.resume();
            }
        }
    }

    private void indexAllChunks(EmbeddingIndexMigration job, String index, String provider, String model, int dimension,
                                String requesterId) throws Exception {
        int page = 0;
        while (true) {
            var slice = chunks.findAll(PageRequest.of(page++, BATCH_SIZE, Sort.by("vectorId")));
            if (slice.isEmpty()) {
                break;
            }
            List<DocumentVector> rows = slice.getContent();
            List<String> texts = rows.stream().map(row -> ContextualChunkText.compose(
                    contextual.isEnabled() ? row.getChunkContext() : null, row.getTextContent())).toList();
            var result = embeddingClient.embedForProvider(texts, requesterId, provider, model, dimension);
            if (result.vectors().size() != rows.size()) {
                throw new IllegalStateException("Embedding 返回的向量数与分块数不同");
            }
            List<EsDocument> docs = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                DocumentVector row = rows.get(i);
                if (result.vectors().get(i).length != dimension) {
                    throw new IllegalStateException("Embedding 返回维度与目标索引不一致");
                }
                docs.add(new EsDocument(row.getFileMd5() + ":" + row.getChunkId(), row.getFileMd5(),
                        row.getChunkId(), row.getTextContent(),
                        contextual.isEnabled() ? row.getChunkContext() : null, row.getPageNumber(),
                        row.getAnchorText(), result.vectors().get(i), result.modelVersion(), row.getUserId(),
                        row.getOrgTag(), row.isPublic()));
            }
            elasticsearchService.bulkIndexToIndex(index, docs);
            job.setProcessedChunks(job.getProcessedChunks() + rows.size());
            save(job);
            if (!slice.hasNext()) {
                break;
            }
        }
    }

    /** Scan MySQL under the document mutation gate; embed only missing or changed rows. */
    private void reconcileIndex(EmbeddingIndexMigration job, String index, String provider, String model,
                                int dimension, String requesterId) throws Exception {
        Set<String> expectedIds = new HashSet<>();
        int page = 0;
        String modelVersion = provider + ":" + model + ":" + dimension;
        while (true) {
            var slice = chunks.findAll(PageRequest.of(page++, BATCH_SIZE, Sort.by("vectorId")));
            if (slice.isEmpty()) {
                break;
            }
            Map<String, DocumentVector> rows = new HashMap<>();
            for (DocumentVector row : slice.getContent()) {
                String id = row.getFileMd5() + ":" + row.getChunkId();
                expectedIds.add(id);
                rows.put(id, row);
            }
            List<String> ids = new ArrayList<>(rows.keySet());
            SearchResponse<EsDocument> found = es.search(s -> s.index(index)
                    .query(q -> q.ids(i -> i.values(ids)))
                    .source(src -> src.filter(f -> f.excludes("vector")))
                    .size(ids.size()), EsDocument.class);
            Map<String, EsDocument> existing = new HashMap<>();
            found.hits().hits().forEach(hit -> existing.put(hit.id(), hit.source()));
            List<DocumentVector> changed = rows.entrySet().stream()
                    .filter(entry -> !sameChunk(entry.getValue(), existing.get(entry.getKey()),
                            modelVersion, contextual.isEnabled()))
                    .map(Map.Entry::getValue).toList();
            if (!changed.isEmpty()) {
                List<String> texts = changed.stream().map(row -> ContextualChunkText.compose(
                        contextual.isEnabled() ? row.getChunkContext() : null, row.getTextContent())).toList();
                var result = embeddingClient.embedForProvider(texts, requesterId, provider, model, dimension);
                if (result.vectors().size() != changed.size()) {
                    throw new IllegalStateException("Embedding 返回的向量数与变化分块数不同");
                }
                List<EsDocument> updates = new ArrayList<>(changed.size());
                for (int i = 0; i < changed.size(); i++) {
                    DocumentVector row = changed.get(i);
                    if (result.vectors().get(i).length != dimension) {
                        throw new IllegalStateException("Embedding 返回维度与目标索引不一致");
                    }
                    updates.add(new EsDocument(row.getFileMd5() + ":" + row.getChunkId(), row.getFileMd5(),
                            row.getChunkId(), row.getTextContent(),
                            contextual.isEnabled() ? row.getChunkContext() : null, row.getPageNumber(),
                            row.getAnchorText(), result.vectors().get(i), result.modelVersion(), row.getUserId(),
                            row.getOrgTag(), row.isPublic()));
                }
                elasticsearchService.bulkIndexToIndex(index, updates);
            }
            job.setProcessedChunks(job.getProcessedChunks() + slice.getNumberOfElements());
            save(job);
            if (!slice.hasNext()) {
                break;
            }
        }
        removeStaleDocuments(index, expectedIds);
    }

    static boolean sameChunk(DocumentVector row, EsDocument indexed, String modelVersion, boolean contextual) {
        return indexed != null
                && Objects.equals(indexed.getTextContent(), row.getTextContent())
                && Objects.equals(indexed.getChunkContext(), contextual ? row.getChunkContext() : null)
                && Objects.equals(indexed.getPageNumber(), row.getPageNumber())
                && Objects.equals(indexed.getAnchorText(), row.getAnchorText())
                && Objects.equals(indexed.getUserId(), row.getUserId())
                && Objects.equals(indexed.getOrgTag(), row.getOrgTag())
                && indexed.isPublic() == row.isPublic()
                && Objects.equals(indexed.getModelVersion(), modelVersion);
    }

    private void removeStaleDocuments(String index, Set<String> expectedIds) throws Exception {
        SearchResponse<EsDocument> page = es.search(s -> s.index(index).size(500)
                .source(src -> src.filter(f -> f.excludes("vector")))
                .scroll(t -> t.time("1m"))
                .query(q -> q.matchAll(m -> m)), EsDocument.class);
        String scrollId = page.scrollId();
        List<co.elastic.clients.elasticsearch.core.search.Hit<EsDocument>> hits = page.hits().hits();
        List<String> stale = new ArrayList<>();
        try {
            while (true) {
                for (var hit : hits) {
                    if (!expectedIds.contains(hit.id())) {
                        stale.add(hit.id());
                    }
                }
                if (hits.size() < 500) {
                    break;
                }
                String currentScrollId = scrollId;
                var next = es.scroll(s -> s.scrollId(currentScrollId).scroll(t -> t.time("1m")), EsDocument.class);
                hits = next.hits().hits();
                scrollId = next.scrollId();
            }
        } finally {
            if (scrollId != null) {
                String finalScrollId = scrollId;
                es.clearScroll(c -> c.scrollId(finalScrollId));
            }
        }
        for (String id : stale) {
            es.delete(d -> d.index(index).id(id));
        }
    }

    private void validateProbes(EmbeddingIndexMigration job, List<Probe> probes, String requesterId) throws Exception {
        for (Probe probe : probes) {
            if (probe == null || probe.question() == null || probe.question().isBlank()
                    || probe.expectedFileMd5() == null || probe.expectedFileMd5().isBlank()) {
                throw new IllegalArgumentException("抽样问题必须包含 question 和 expectedFileMd5");
            }
            float[] vector = embeddingClient.embedForProvider(List.of(probe.question()), requesterId,
                    job.getTargetProvider(), job.getTargetModel(), job.getTargetDimension()).vectors().get(0);
            List<Float> queryVector = new ArrayList<>(vector.length);
            for (float value : vector) {
                queryVector.add(value);
            }
            SearchResponse<EsDocument> response = es.search(s -> s.index(job.getTargetIndex())
                    .knn(k -> k.field("vector").queryVector(queryVector).k(5).numCandidates(50))
                    .size(5), EsDocument.class);
            boolean found = response.hits().hits().stream().anyMatch(hit -> hit.source() != null
                    && probe.expectedFileMd5().equals(hit.source().getFileMd5()));
            if (!found) {
                throw new IllegalStateException("抽样问题未命中预期文件：" + probe.question());
            }
        }
    }

    public synchronized EmbeddingIndexMigration rollback(String requesterId) throws Exception {
        EmbeddingIndexMigration job = requiredJob(SWITCHED);
        if (!activePhysicalIndex().equals(job.getTargetIndex())) {
            throw new IllegalStateException("别名已改变，不能按当前记录回滚");
        }
        providers.getEmbeddingProvider(job.getPreviousProvider());
        job.setStatus(ROLLING_BACK);
        job.setProcessedChunks(0);
        job.setErrorMessage(null);
        save(job);
        worker.execute(() -> rebuildOldAndRollback(requesterId));
        return job;
    }

    private void rebuildOldAndRollback(String requesterId) {
        MessageListenerContainer consumer = listeners.getListenerContainer("fileProcessing");
        try {
            if (consumer == null || !consumer.isRunning()) {
                throw new IllegalStateException("文件消费监听器未运行，无法安全回滚");
            }
            consumer.pause();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!consumer.isContainerPaused() && System.nanoTime() < deadline) {
                Thread.sleep(200);
            }
            if (!consumer.isContainerPaused()) {
                throw new IllegalStateException("文件消费未能在 30 秒内暂停");
            }
            try (EmbeddingSwitchGate.Permit mutation = DocumentMutationGate.write()) {
            EmbeddingIndexMigration job = jobs.findById(1L).orElseThrow();
            reconcileIndex(job, job.getPreviousIndex(), job.getPreviousProvider(), job.getPreviousModel(),
                    job.getPreviousDimension(), requesterId);
            es.indices().refresh(r -> r.index(job.getPreviousIndex()));
            long actual = es.count(c -> c.index(job.getPreviousIndex())).count();
            long expected = chunks.countDistinctChunks();
            if (actual != expected) {
                throw new IllegalStateException("旧索引重建后文档数不一致：ES=" + actual + "，MySQL=" + expected);
            }
            try (EmbeddingSwitchGate.Permit ignored = EmbeddingSwitchGate.write()) {
                if (!activePhysicalIndex().equals(job.getTargetIndex())) {
                    throw new IllegalStateException("当前别名已被其他任务切换");
                }
                es.indices().updateAliases(u -> u.actions(a -> a.remove(r -> r.index(job.getTargetIndex()).alias(KnowledgeIndex.ALIAS)))
                        .actions(a -> a.add(add -> add.index(job.getPreviousIndex()).alias(KnowledgeIndex.ALIAS))));
                try {
                    providers.activateRebuiltEmbeddingProvider(job.getPreviousProvider(), job.getPreviousModel(),
                            job.getPreviousDimension(), requesterId);
                } catch (Exception e) {
                    es.indices().updateAliases(u -> u.actions(a -> a.remove(r -> r.index(job.getPreviousIndex()).alias(KnowledgeIndex.ALIAS)))
                            .actions(a -> a.add(add -> add.index(job.getTargetIndex()).alias(KnowledgeIndex.ALIAS))));
                    throw e;
                }
            }
            job.setStatus(ROLLED_BACK);
            save(job);
            }
        } catch (Exception e) {
            jobs.findById(1L).ifPresent(job -> {
                job.setStatus(SWITCHED);
                String message = e.getMessage();
                job.setErrorMessage(message == null ? e.getClass().getSimpleName()
                        : message.substring(0, Math.min(message.length(), 500)));
                save(job);
            });
        } finally {
            if (consumer != null && consumer.isRunning()) {
                consumer.resume();
            }
        }
    }

    private String activePhysicalIndex() throws Exception {
        var aliases = es.indices().getAlias(g -> g.name(KnowledgeIndex.ALIAS)).result();
        if (aliases.size() != 1) {
            throw new IllegalStateException("知识库别名必须恰好指向一个物理索引");
        }
        return aliases.keySet().iterator().next();
    }

    private EmbeddingIndexMigration requiredJob(String status) {
        EmbeddingIndexMigration job = jobs.findById(1L).orElseThrow(() -> new IllegalStateException("没有重建任务"));
        if (!status.equals(job.getStatus())) {
            throw new IllegalStateException("当前任务状态为 " + job.getStatus() + "，需要 " + status);
        }
        return job;
    }

    private void fail(Exception error) {
        jobs.findById(1L).ifPresent(job -> {
            job.setStatus(FAILED);
            String message = error.getMessage();
            job.setErrorMessage(message == null ? error.getClass().getSimpleName()
                    : message.substring(0, Math.min(message.length(), 500)));
            save(job);
        });
    }

    private void save(EmbeddingIndexMigration job) {
        job.setUpdatedAt(LocalDateTime.now());
        jobs.save(job);
    }

    public record Probe(String question, String expectedFileMd5) {
    }
}
