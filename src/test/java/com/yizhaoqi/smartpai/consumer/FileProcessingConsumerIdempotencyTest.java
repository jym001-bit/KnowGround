package com.yizhaoqi.smartpai.consumer;

import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.service.ElasticsearchService;
import com.yizhaoqi.smartpai.service.FileTaskClaimService;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.UploadService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import com.yizhaoqi.smartpai.service.conflict.DocumentConflictService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class FileProcessingConsumerIdempotencyTest {
    @Test
    void duplicateKafkaDeliveryDoesNotParseOrCallEmbedding() {
        ParseService parser = mock(ParseService.class);
        VectorizationService vectorizer = mock(VectorizationService.class);
        DocumentService documents = mock(DocumentService.class);
        FileTaskClaimService claims = mock(FileTaskClaimService.class);
        FileProcessingTask task = new FileProcessingTask("abcdef", null, "x.txt", "user", null,
                false, FileProcessingTask.TASK_TYPE_UPLOAD_PROCESS, "user");
        task.setTaskId("event-1");
        when(claims.claim(task)).thenReturn(null);

        FileProcessingConsumer consumer = new FileProcessingConsumer(parser, vectorizer, documents,
                mock(DocumentConflictService.class), claims, mock(FileUploadRepository.class),
                mock(DocumentVectorRepository.class), mock(ElasticsearchService.class), mock(UploadService.class));
        consumer.processTask(task);

        verifyNoInteractions(parser, vectorizer, documents);
    }
}
