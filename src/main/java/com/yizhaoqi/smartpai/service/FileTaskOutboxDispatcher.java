package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.model.FileTaskOutbox;
import com.yizhaoqi.smartpai.repository.FileTaskOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class FileTaskOutboxDispatcher {
    private final FileTaskOutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final KafkaConfig kafkaConfig;
    private final ObjectMapper mapper;
    private final OutboxState state;

    public FileTaskOutboxDispatcher(FileTaskOutboxRepository outbox, KafkaTemplate<String, Object> kafka,
                                    KafkaConfig kafkaConfig, ObjectMapper mapper, OutboxState state) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.kafkaConfig = kafkaConfig;
        this.mapper = mapper;
        this.state = state;
    }

    @Scheduled(fixedDelayString = "${file-processing.outbox.poll-ms:1000}")
    public void dispatch() {
        for (String id : outbox.dueIds(LocalDateTime.now(), PageRequest.of(0, 50))) {
            if (!state.claim(id)) continue;
            try {
                FileTaskOutbox event = outbox.findById(id).orElseThrow();
                FileProcessingTask task = mapper.readValue(event.getPayload(), FileProcessingTask.class);
                kafka.executeInTransaction(kt -> {
                    try {
                        kt.send(kafkaConfig.getFileProcessingTopic(), task.getFileMd5(), task)
                                .get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("Kafka 投递失败", e);
                    }
                    return true;
                });
                state.sent(id);
            } catch (Exception e) {
                log.warn("Outbox 投递失败，稍后重试: taskId={}", id, e);
                state.retry(id, e);
            }
        }
    }

    @Service
    public static class OutboxState {
        private final FileTaskOutboxRepository outbox;

        public OutboxState(FileTaskOutboxRepository outbox) { this.outbox = outbox; }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public boolean claim(String id) {
            LocalDateTime now = LocalDateTime.now();
            return outbox.claim(id, now, now.plusMinutes(2)) == 1;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void sent(String id) {
            FileTaskOutbox event = outbox.findById(id).orElseThrow();
            event.setStatus("SENT");
            event.setLeaseUntil(null);
            outbox.save(event);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void retry(String id, Exception failure) {
            FileTaskOutbox event = outbox.findById(id).orElseThrow();
            int attempts = event.getAttempts() + 1;
            long cap = Math.min(300, 1L << Math.min(attempts, 8));
            event.setAttempts(attempts);
            event.setStatus("PENDING");
            event.setLeaseUntil(null);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(cap + ThreadLocalRandom.current().nextLong(cap + 1)));
            event.setLastError(failure.toString().substring(0, Math.min(1000, failure.toString().length())));
            outbox.save(event);
        }
    }
}
