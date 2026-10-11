package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentConflict;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentConflictRepository extends JpaRepository<DocumentConflict, Long> {
    Optional<DocumentConflict> findByPairKey(String pairKey);
    List<DocumentConflict> findByStatusOrderByCreatedAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocumentConflict c where c.id = :id")
    Optional<DocumentConflict> findForReview(@Param("id") Long id);

    List<DocumentConflict> findByLeftFileMd5OrRightFileMd5(String leftFileMd5, String rightFileMd5);
}
