package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.service.chunk.ContextualChunkText;
import com.yizhaoqi.smartpai.service.contextual.ChunkContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

// 向量化服务类
@Service
public class VectorizationService {

    private static final Logger logger = LoggerFactory.getLogger(VectorizationService.class);

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Autowired
    private ChunkContextService chunkContextService;

    /**
     * 执行向量化操作
     * @param fileMd5 文件指纹
     * @param userId 上传用户ID
     * @param orgTag 组织标签
     * @param isPublic 是否公开
     */
    public void vectorize(String fileMd5, String userId, String orgTag, boolean isPublic) {
        vectorizeWithUsage(fileMd5, userId, orgTag, isPublic, userId);
    }

    public void vectorize(String fileMd5, String userId, String orgTag, boolean isPublic, String requesterId) {
        vectorizeWithUsage(fileMd5, userId, orgTag, isPublic, requesterId);
    }

    public VectorizationUsageResult vectorizeWithUsage(String fileMd5, String userId, String orgTag, boolean isPublic, String requesterId) {
        try {
            logger.info("开始向量化文件，fileMd5: {}, userId: {}, orgTag: {}, isPublic: {}", 
                       fileMd5, userId, orgTag, isPublic);
                       
            boolean contextual = chunkContextService.isEnabled();
            if (contextual) {
                // 说明生成失败时仍按原文入库，已成功生成的说明可以复用。
                try {
                    chunkContextService.ensureContexts(fileMd5);
                } catch (Exception e) {
                    logger.warn("生成分块上下文失败，继续按原文向量化: fileMd5={}", fileMd5, e);
                }
            }
            List<DocumentVector> chunks = documentVectorRepository.findByFileMd5OrderByChunkIdAsc(fileMd5);
            if (chunks == null || chunks.isEmpty()) {
                logger.warn("未找到分块内容，fileMd5: {}", fileMd5);
                return new VectorizationUsageResult(0, 0, embeddingClient.currentModelVersion());
            }

            // 提取文本内容
            List<String> texts = chunks.stream()
                    .map(chunk -> ContextualChunkText.compose(
                            contextual ? chunk.getChunkContext() : null, chunk.getTextContent()))
                    .toList();

            // 调用外部模型生成向量
            EmbeddingClient.EmbeddingUsageResult embeddingResult = embeddingClient.embedWithUsage(
                    texts,
                    requesterId,
                    EmbeddingClient.UsageType.UPLOAD
            );
            List<float[]> vectors = embeddingResult.vectors();

            // 构建 Elasticsearch 文档并存储
            List<EsDocument> esDocuments = IntStream.range(0, chunks.size())
                    .mapToObj(i -> new EsDocument(
                            UUID.randomUUID().toString(),
                            fileMd5,
                            chunks.get(i).getChunkId(),
                            chunks.get(i).getTextContent(),
                            contextual ? chunks.get(i).getChunkContext() : null,
                            chunks.get(i).getPageNumber(),
                            chunks.get(i).getAnchorText(),
                            vectors.get(i),
                            embeddingResult.modelVersion(),
                            userId,
                            orgTag,
                            isPublic
                    ))
                    .toList();

            elasticsearchService.bulkIndex(esDocuments); // 不是一条一条请求 Elasticsearch,批量存储到 Elasticsearch

            logger.info("向量化完成，fileMd5: {}", fileMd5);
            return new VectorizationUsageResult(
                    embeddingResult.totalTokens(),
                    chunks.size(),
                    embeddingResult.modelVersion()
            );
        } catch (Exception e) {
            logger.error("向量化失败，fileMd5: {}", fileMd5, e);
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                throw new RuntimeException("向量化失败", e);
            }
            throw new RuntimeException("向量化失败: " + message, e);
        }
    }
    

    public record VectorizationUsageResult(int actualEmbeddingTokens, int actualChunkCount, String modelVersion) {
    }
}
