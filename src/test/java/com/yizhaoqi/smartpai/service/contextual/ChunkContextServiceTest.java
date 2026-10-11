package com.yizhaoqi.smartpai.service.contextual;

import com.yizhaoqi.smartpai.config.ContextualRetrievalProperties;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChunkContextServiceTest {

    private final ContextualRetrievalProperties properties = new ContextualRetrievalProperties();
    private final ChunkContextGenerator generator = mock(ChunkContextGenerator.class);
    private final DocumentVectorRepository repository = mock(DocumentVectorRepository.class);
    private final FileUploadRepository fileUploadRepository = mock(FileUploadRepository.class);
    private final List<DocumentVector> chunks = new ArrayList<>();
    private ChunkContextService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setChunksPerRequest(8);
        properties.setNeighbourChunks(1);
        properties.setConcurrency(2);
        service = new ChunkContextService(properties, generator, repository, fileUploadRepository, Runnable::run);

        FileUpload upload = new FileUpload();
        upload.setFileMd5("md5");
        upload.setFileName("部署手册.pdf");
        when(fileUploadRepository.findFirstByFileMd5OrderByCreatedAtDesc("md5")).thenReturn(Optional.of(upload));
        when(repository.findByFileMd5OrderByChunkIdAsc(eq("md5"), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            int from = (int) Math.min(pageable.getOffset(), chunks.size());
            int to = Math.min(from + pageable.getPageSize(), chunks.size());
            return new SliceImpl<>(List.copyOf(chunks.subList(from, to)), pageable, to < chunks.size());
        });
        when(repository.findByFileMd5AndChunkIdBetweenOrderByChunkIdAsc(eq("md5"), anyInt(), anyInt())).thenAnswer(invocation -> {
            int from = invocation.getArgument(1);
            int to = invocation.getArgument(2);
            return chunks.stream().filter(c -> c.getChunkId() >= from && c.getChunkId() <= to).toList();
        });
    }

    @Test
    void generatesContextsBatchByBatchAndSavesThem() throws Exception {
        addChunks(10);
        when(generator.generate(anyString(), any())).thenAnswer(invocation -> {
            Set<Integer> ids = invocation.getArgument(1);
            Map<Integer, String> contexts = new HashMap<>();
            ids.forEach(id -> contexts.put(id, "说明-" + id));
            return contexts;
        });

        ChunkContextService.ContextualizationResult result = service.ensureContexts("md5");

        assertEquals(10, result.requestedChunks());
        assertEquals(10, result.generatedChunks());
        assertEquals(0, result.failedBatches());
        assertEquals("说明-7", chunks.get(6).getChunkContext());
        verify(generator, times(2)).generate(anyString(), any());
        // 第二批 9-10 的窗口带上前后各 1 个相邻分块
        verify(repository).findByFileMd5AndChunkIdBetweenOrderByChunkIdAsc("md5", 8, 11);
        verify(repository, times(2)).saveAll(any());

        ArgumentCaptor<String> windows = ArgumentCaptor.forClass(String.class);
        verify(generator, times(2)).generate(windows.capture(), any());
        assertTrue(windows.getAllValues().get(0).startsWith("文档标题：部署手册.pdf"));
    }

    @Test
    void failedBatchFallsBackWithoutFailingIngestionAndOtherBatchesContinue() throws Exception {
        addChunks(16);
        when(generator.generate(anyString(), any()))
                .thenThrow(new IllegalStateException("llm down"))
                .thenReturn(Map.of(9, "说明-9", 10, "说明-10"));

        ChunkContextService.ContextualizationResult result = service.ensureContexts("md5");

        assertEquals(16, result.requestedChunks());
        assertEquals(2, result.generatedChunks(), "模型漏掉的分块按原文入索引");
        assertEquals(1, result.failedBatches());
        assertNull(chunks.get(0).getChunkContext());
        assertEquals("说明-9", chunks.get(8).getChunkContext());
        assertNull(chunks.get(10).getChunkContext());
    }

    @Test
    void skipsChunksThatAlreadyHaveContexts() throws Exception {
        addChunks(4);
        chunks.forEach(c -> c.setChunkContext("已有"));

        ChunkContextService.ContextualizationResult result = service.ensureContexts("md5");

        assertEquals(0, result.requestedChunks());
        verify(generator, never()).generate(anyString(), any());
    }

    @Test
    void disabledDoesNothing() {
        properties.setEnabled(false);

        assertEquals(0, service.ensureContexts("md5").requestedChunks());
        verifyNoInteractions(generator, repository, fileUploadRepository);
    }

    private void addChunks(int count) {
        IntStream.rangeClosed(1, count).forEach(id -> {
            DocumentVector vector = new DocumentVector();
            vector.setFileMd5("md5");
            vector.setChunkId(id);
            vector.setTextContent("分块" + id + "原文。");
            chunks.add(vector);
        });
    }
}
