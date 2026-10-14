package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.repository.FileTaskDeadLetterRepository;
import com.yizhaoqi.smartpai.repository.FileTaskExecutionRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileTaskClaimServiceTest {
    @Test
    void databasePrimaryKeyPreventsSecondOwnerFromClaimingSameTask() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:claims;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create table if not exists file_task_execution (task_id varchar(100) primary key, "
                + "file_md5 varchar(32), status varchar(16), owner varchar(36), lease_until timestamp, "
                + "heartbeat_at timestamp, recovery_count int not null default 0)");
        FileTaskExecutionRepository executions = mock(FileTaskExecutionRepository.class);
        FileTaskClaimService claims = new FileTaskClaimService(jdbc, executions,
                mock(FileTaskDeadLetterRepository.class));
        FileProcessingTask task = new FileProcessingTask("abcdef", null, "x.txt", "user", null,
                false, FileProcessingTask.TASK_TYPE_UPLOAD_PROCESS, "user");
        task.setTaskId("same-event");

        assertNotNull(claims.claim(task));
        assertThrows(TaskLeaseBusyException.class, () -> claims.claim(task));
        assertEquals(1, jdbc.queryForObject("select count(*) from file_task_execution", Integer.class));
        verify(executions).claim(eq("same-event"), anyString(), any(), any());
    }
}
