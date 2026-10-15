package com.yizhaoqi.smartpai.consumer;

import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.service.FileTaskClaimService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class FileTaskDeadLetterConsumer {
    private final FileTaskClaimService claims;

    public FileTaskDeadLetterConsumer(FileTaskClaimService claims) { this.claims = claims; }

    @KafkaListener(id = "fileProcessingDeadLetters", topics = "#{kafkaConfig.getFileProcessingDltTopic()}",
            groupId = "#{kafkaConfig.getFileProcessingGroupId()}-dead-letter")
    public void onDeadLetter(ConsumerRecord<String, FileProcessingTask> record) {
        claims.recordDeadLetter(record.value(), "Kafka 重试耗尽，offset=" + record.offset());
    }
}
