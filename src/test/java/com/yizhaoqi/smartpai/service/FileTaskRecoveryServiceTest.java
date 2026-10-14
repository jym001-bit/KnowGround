package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.repository.FileTaskDeadLetterRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileTaskRecoveryServiceTest {
    @Test
    void staleTaskIsRedeliveredAtMostTwiceThenFailedAndAlerted() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:recovery;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create table file_task_execution (task_id varchar(100) primary key, file_md5 varchar(32), "
                + "status varchar(16), owner varchar(36), lease_until timestamp, heartbeat_at timestamp, "
                + "recovery_count int, last_error varchar(1000))");
        jdbc.execute("create table file_task_outbox (id varchar(36) primary key, status varchar(16), "
                + "next_attempt_at timestamp, lease_until timestamp)");
        jdbc.execute("create table file_upload (file_md5 varchar(32), vectorization_status varchar(32), "
                + "vectorization_error_message varchar(1000))");
        FileTaskDeadLetterRepository alerts = mock(FileTaskDeadLetterRepository.class);
        when(alerts.findById("task-1")).thenReturn(Optional.empty());
        FileTaskRecoveryService recovery = new FileTaskRecoveryService(jdbc, alerts,
                new DataSourceTransactionManager(source));
        LocalDateTime old = LocalDateTime.now().minusMinutes(11);
        jdbc.update("insert into file_task_execution values (?, ?, 'PROCESSING', 'worker', ?, ?, 0, null)",
                "task-1", "file-md5", old, old);
        jdbc.update("insert into file_task_outbox values ('task-1', 'SENT', ?, null)", old);
        jdbc.update("insert into file_upload values ('file-md5', 'PROCESSING', null)");

        for (int attempt = 1; attempt <= 3; attempt++) {
            recovery.recover("task-1", LocalDateTime.now().minusMinutes(10));
            assertEquals(attempt, jdbc.queryForObject(
                    "select recovery_count from file_task_execution where task_id='task-1'", Integer.class));
            if (attempt < 3) {
                assertEquals("PENDING", jdbc.queryForObject(
                        "select status from file_task_outbox where id='task-1'", String.class));
                jdbc.update("update file_task_execution set status='PROCESSING', owner='worker', heartbeat_at=? "
                        + "where task_id='task-1'", old);
                jdbc.update("update file_task_outbox set status='SENT' where id='task-1'");
            }
        }
        assertEquals("EXHAUSTED", jdbc.queryForObject(
                "select status from file_task_execution where task_id='task-1'", String.class));
        assertEquals("FAILED", jdbc.queryForObject(
                "select vectorization_status from file_upload where file_md5='file-md5'", String.class));
        verify(alerts, times(1)).save(any());
    }
}
