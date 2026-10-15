package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileTaskDeadLetter;
import com.yizhaoqi.smartpai.repository.FileTaskDeadLetterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FileTaskRecoveryService {
    private static final String TIMEOUT_REASON = "处理心跳超过 10 分钟未更新";
    private final JdbcTemplate jdbc;
    private final FileTaskDeadLetterRepository deadLetters;
    private final TransactionTemplate transactions;

    public FileTaskRecoveryService(JdbcTemplate jdbc, FileTaskDeadLetterRepository deadLetters,
                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.deadLetters = deadLetters;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scan() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<String> ids = jdbc.queryForList(
                "select task_id from file_task_execution where status='PROCESSING' "
                        + "and heartbeat_at < ? order by heartbeat_at limit 100", String.class, cutoff);
        for (String id : ids) {
            try {
                recover(id, cutoff);
            } catch (Exception e) {
                log.error("恢复超时文件任务失败: taskId={}", id, e);
            }
        }
    }

    public void recover(String id, LocalDateTime cutoff) {
        transactions.executeWithoutResult(ignored -> recoverInTransaction(id, cutoff));
    }

    private void recoverInTransaction(String id, LocalDateTime cutoff) {
        // 条件更新是多实例抢占锁；心跳刚更新或已由其他实例处理时，更新数量为 0。
        int claimed = jdbc.update("update file_task_execution set status='RECOVERING', owner=null, lease_until=null, "
                        + "recovery_count=recovery_count+1, last_error=? where task_id=? and status='PROCESSING' "
                        + "and heartbeat_at < ?", TIMEOUT_REASON, id, cutoff);
        if (claimed == 0) return;

        List<TaskRow> rows = jdbc.query("select file_md5, recovery_count from file_task_execution where task_id=?",
                (rs, n) -> new TaskRow(rs.getString(1), rs.getInt(2)), id);
        if (rows.isEmpty()) return;
        TaskRow row = rows.get(0);
        if (row.recoveryCount() >= 3) {
            fail(id, row.fileMd5(), "超时恢复已达 3 次");
            return;
        }
        int sent = jdbc.update("update file_task_outbox set status='PENDING', next_attempt_at=?, lease_until=null "
                + "where id=? and status='SENT'", LocalDateTime.now(), id);
        if (sent == 0) {
            fail(id, row.fileMd5(), "找不到已投递的 Outbox 事件，无法安全重投");
            return;
        }
        log.warn("超时文件任务已重新投递: taskId={}, recoveryCount={}", id, row.recoveryCount());
    }

    private void fail(String id, String fileMd5, String reason) {
        jdbc.update("update file_task_execution set status='EXHAUSTED', last_error=? where task_id=?", reason, id);
        jdbc.update("update file_upload set vectorization_status='FAILED', vectorization_error_message=? "
                + "where file_md5=? and vectorization_status='PROCESSING'", reason, fileMd5);
        FileTaskDeadLetter alert = deadLetters.findById(id).orElseGet(FileTaskDeadLetter::new);
        alert.setTaskId(id);
        alert.setFileMd5(fileMd5);
        alert.setTaskType("RECOVERY");
        alert.setReason(reason);
        deadLetters.save(alert);
        log.error("文件处理恢复失败告警: taskId={}, fileMd5={}, reason={}", id, fileMd5, reason);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failLegacyTasksOnStartup() {
        // 旧上传没有分片时间；从本次升级开始重新计 24 小时，避免误删刚上传的旧文件。
        jdbc.update("update file_upload set last_chunk_at=? where status=0 and last_chunk_at is null",
                LocalDateTime.now());
        // 升级前任务没有心跳，不能推断其工作是否安全可续；一次批量置失败，不进 Kafka。
        int executions = jdbc.update("update file_task_execution set status='EXHAUSTED', owner=null, lease_until=null, "
                + "last_error='旧版任务缺少心跳，请手动重试' where status='PROCESSING' and heartbeat_at is null");
        jdbc.update("update file_upload f set f.vectorization_status='FAILED', "
                + "f.vectorization_error_message='旧版任务缺少心跳，请手动重试' "
                + "where f.vectorization_status='PROCESSING' and exists "
                + "(select 1 from file_task_execution e where e.file_md5=f.file_md5 "
                + "and e.status='EXHAUSTED' and e.heartbeat_at is null)");
        int files = jdbc.update("update file_upload f set f.vectorization_status='FAILED', "
                        + "f.vectorization_error_message='旧版任务缺少心跳，请手动重试' "
                        + "where f.vectorization_status='PROCESSING' and f.created_at < ? "
                        + "and not exists (select 1 from file_task_outbox o where o.file_md5=f.file_md5)",
                LocalDateTime.now().minusMinutes(10));
        if (executions + files > 0) {
            log.warn("历史文件任务已快速置失败: executionRows={}, fileRows={}", executions, files);
        }
    }

    private record TaskRow(String fileMd5, int recoveryCount) { }
}
