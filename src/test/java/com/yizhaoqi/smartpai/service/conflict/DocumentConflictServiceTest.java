package com.yizhaoqi.smartpai.service.conflict;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.DocumentConflict;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentConflictRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.LlmProviderRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentConflictServiceTest {
    private DocumentConflictRepository conflicts;
    private FileUploadRepository uploads;
    private DocumentConflictService service;
    private DocumentConflict conflict;

    @BeforeEach
    void setUp() {
        conflicts = mock(DocumentConflictRepository.class);
        uploads = mock(FileUploadRepository.class);
        service = new DocumentConflictService(mock(ElasticsearchClient.class), conflicts, uploads,
                mock(LlmProviderRouter.class), new ObjectMapper(), mock(ThreadPoolTaskExecutor.class), true, false);
        conflict = new DocumentConflict();
        conflict.setId(1L);
        conflict.setLeftFileMd5("left");
        conflict.setRightFileMd5("right");
        conflict.setStatus(DocumentConflict.PENDING);
        when(conflicts.findForReview(1L)).thenReturn(Optional.of(conflict));
        when(uploads.findSuppressedFileMd5s()).thenReturn(List.of());
        when(conflicts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void keepingOneDocumentSuppressesTheOther() {
        FileUpload loser = new FileUpload();
        loser.setFileMd5("right");
        when(uploads.findAllByFileMd5("right")).thenReturn(List.of(loser));

        DocumentConflict decided = service.decide(1L, "left", false, "admin");

        assertEquals(DocumentConflict.KEEP_LEFT, decided.getStatus());
        assertTrue(loser.getSearchSuppressed());
        assertEquals("admin", decided.getReviewedBy());
        verify(uploads).saveAll(List.of(loser));
    }

    @Test
    void dismissingFalsePositiveLeavesDocumentsSearchable() {
        DocumentConflict decided = service.decide(1L, null, true, "admin");
        assertEquals(DocumentConflict.DISMISSED, decided.getStatus());
        verify(uploads, never()).saveAll(any());
    }

    @Test
    void cannotKeepUnrelatedDocumentOrReviewTwice() {
        assertThrows(IllegalArgumentException.class, () -> service.decide(1L, "unknown", false, "admin"));
        assertFalse(conflict.getStatus().equals(DocumentConflict.KEEP_LEFT));
        conflict.setStatus(DocumentConflict.KEEP_LEFT);
        assertThrows(IllegalStateException.class, () -> service.decide(1L, "right", false, "admin"));
    }

    @Test
    void undoRestoresSearchWhenNoOtherDecisionSuppressesTheFile() {
        conflict.setStatus(DocumentConflict.KEEP_LEFT);
        FileUpload loser = new FileUpload();
        loser.setFileMd5("right");
        loser.setSearchSuppressed(true);
        when(uploads.findAllByFileMd5("right")).thenReturn(List.of(loser));
        when(conflicts.findByLeftFileMd5OrRightFileMd5("right", "right")).thenReturn(List.of(conflict));

        service.undo(1L);

        assertEquals(DocumentConflict.PENDING, conflict.getStatus());
        assertFalse(loser.getSearchSuppressed());
    }

    @Test
    void undoKeepsFileSuppressedWhenAnotherDecisionStillExcludesIt() {
        conflict.setStatus(DocumentConflict.KEEP_LEFT);
        DocumentConflict another = new DocumentConflict();
        another.setId(2L);
        another.setLeftFileMd5("third");
        another.setRightFileMd5("right");
        another.setStatus(DocumentConflict.KEEP_LEFT);
        FileUpload loser = new FileUpload();
        loser.setFileMd5("right");
        loser.setSearchSuppressed(true);
        when(uploads.findAllByFileMd5("right")).thenReturn(List.of(loser));
        when(conflicts.findByLeftFileMd5OrRightFileMd5("right", "right"))
                .thenReturn(List.of(conflict, another));

        service.undo(1L);

        assertTrue(loser.getSearchSuppressed());
    }
}
