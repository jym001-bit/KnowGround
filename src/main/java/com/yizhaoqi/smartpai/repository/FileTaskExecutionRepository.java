package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileTaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface FileTaskExecutionRepository extends JpaRepository<FileTaskExecution, String> {
    @Modifying
    @Transactional
    @Query("update FileTaskExecution e set e.status = 'PROCESSING', e.owner = :owner, e.leaseUntil = :lease "
            + "where e.taskId = :id and (e.status = 'FAILED' or (e.status = 'PROCESSING' and e.leaseUntil < :now))")
    int claim(@Param("id") String id, @Param("owner") String owner,
              @Param("now") LocalDateTime now, @Param("lease") LocalDateTime lease);

    @Modifying
    @Transactional
    @Query("update FileTaskExecution e set e.leaseUntil = :lease where e.taskId = :id "
            + "and e.owner = :owner and e.status = 'PROCESSING'")
    int renew(@Param("id") String id, @Param("owner") String owner, @Param("lease") LocalDateTime lease);

    @Modifying
    @Transactional
    @Query("update FileTaskExecution e set e.status = :status, e.leaseUntil = null, e.lastError = :error "
            + "where e.taskId = :id and e.owner = :owner and e.status = 'PROCESSING'")
    int finish(@Param("id") String id, @Param("owner") String owner,
               @Param("status") String status, @Param("error") String error);
}
