package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.model.FileTaskOutbox;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileTaskOutboxRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FileTaskOutboxService {
    private final FileUploadRepository files;
    private final FileTaskOutboxRepository outbox;
    private final ObjectMapper mapper;

    public FileTaskOutboxService(FileUploadRepository files, FileTaskOutboxRepository outbox, ObjectMapper mapper) {
        this.files = files;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public void completeUploadAndEnqueue(Long fileId, FileProcessingTask task) {
        FileUpload file = files.findById(fileId).orElseThrow();
        if (file.getStatus() != FileUpload.STATUS_MERGING) {
            throw new IllegalStateException("文件已不处于合并状态");
        }
        file.setStatus(FileUpload.STATUS_COMPLETED);
        file.setVectorizationStatus(FileUpload.VECTORIZATION_STATUS_PENDING);
        file.setVectorizationErrorMessage(null);
        file.setActualEmbeddingTokens(null);
        file.setActualChunkCount(null);
        files.save(file);
        saveTask(task);
    }

    @Transactional
    public void enqueueRetry(FileProcessingTask task) {
        saveTask(task);
    }

    private void saveTask(FileProcessingTask task) {
        task.setTaskId(UUID.randomUUID().toString());
        FileTaskOutbox event = new FileTaskOutbox();
        event.setId(task.getTaskId());
        event.setFileMd5(task.getFileMd5());
        try {
            event.setPayload(mapper.writeValueAsString(task));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("文件任务序列化失败", e);
        }
        outbox.save(event);
    }
}
