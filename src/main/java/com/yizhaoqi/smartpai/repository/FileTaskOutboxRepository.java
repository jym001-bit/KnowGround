package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileTaskOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FileTaskOutboxRepository extends JpaRepository<FileTaskOutbox, String> {
    @Query("select o.id from FileTaskOutbox o where (o.status = 'PENDING' and o.nextAttemptAt <= :now) "
            + "or (o.status = 'SENDING' and o.leaseUntil < :now) order by o.nextAttemptAt")
    List<String> dueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("update FileTaskOutbox o set o.status = 'SENDING', o.leaseUntil = :lease "
            + "where o.id = :id and ((o.status = 'PENDING' and o.nextAttemptAt <= :now) "
            + "or (o.status = 'SENDING' and o.leaseUntil < :now))")
    int claim(@Param("id") String id, @Param("now") LocalDateTime now, @Param("lease") LocalDateTime lease);
}
