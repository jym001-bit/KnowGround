package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.model.FileTaskDeadLetter;
import com.yizhaoqi.smartpai.repository.FileTaskDeadLetterRepository;
import com.yizhaoqi.smartpai.repository.FileTaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FileTaskClaimService {
    private final JdbcTemplate jdbc;
    private final FileTaskExecutionRepository executions;
    private final FileTaskDeadLetterRepository deadLetters;
    private final Map<String, String> active = new ConcurrentHashMap<>();

    public FileTaskClaimService(JdbcTemplate jdbc, FileTaskExecutionRepository executions,
                                FileTaskDeadLetterRepository deadLetters) {
        this.jdbc = jdbc;
        this.executions = executions;
        this.deadLetters = deadLetters;
    }

    public String taskId(FileProcessingTask task) {
        if (task.getTaskId() != null && !task.getTaskId().isBlank()) return task.getTaskId();
        String key = task.getFileMd5() + ":" + task.getTaskType() + ":" + task.getRequesterId();
        return "legacy-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public String claim(FileProcessingTask task) {
        String id = taskId(task);
        String owner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lease = now.plusMinutes(2);
        try {
            jdbc.update("insert into file_task_execution (task_id, file_md5, status, owner, lease_until) values (?, ?, 'PROCESSING', ?, ?)",
                    id, task.getFileMd5(), owner, lease);
            active.put(id, owner);
            return owner;
        } catch (DataIntegrityViolationException duplicate) {
            // 唯一主键由数据库仲裁并发认领；已完成或租约未到期的重复消息直接跳过。
            if (executions.claim(id, owner, now, lease) == 1) {
                active.put(id, owner);
                return owner;
            }
            if (executions.findById(id).map(row -> "COMPLETED".equals(row.getStatus())).orElse(false)) {
                return null;
            }
            throw new TaskLeaseBusyException(id);
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void renewActiveLeases() {
        active.forEach((id, owner) -> {
            try {
                if (executions.renew(id, owner, LocalDateTime.now().plusMinutes(2)) == 0) active.remove(id, owner);
            } catch (Exception e) {
                log.error("文件任务租约续期失败: taskId={}", id, e);
            }
        });
    }

    public void finish(FileProcessingTask task, String owner, boolean success, Exception error) {
        String id = taskId(task);
        try {
            String message = error == null ? null : error.toString();
            if (message != null && message.length() > 1000) message = message.substring(0, 1000);
            executions.finish(id, owner, success ? "COMPLETED" : "FAILED", message);
        } finally {
            active.remove(id, owner);
        }
    }

    @Transactional
    public void recordDeadLetter(FileProcessingTask task, String reason) {
        String id = taskId(task);
        FileTaskDeadLetter row = deadLetters.findById(id).orElseGet(FileTaskDeadLetter::new);
        row.setTaskId(id);
        row.setFileMd5(task.getFileMd5());
        row.setTaskType(task.getTaskType() == null ? "UPLOAD_PROCESS" : task.getTaskType());
        row.setReason(reason == null ? "Kafka 重试耗尽" : reason.substring(0, Math.min(1000, reason.length())));
        deadLetters.save(row);
        log.error("文件处理死信告警: taskId={}, fileMd5={}, reason={}", id, task.getFileMd5(), row.getReason());
    }
}
