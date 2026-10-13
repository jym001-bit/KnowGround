package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_task_outbox")
@Getter
@Setter
public class FileTaskOutbox {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    private LocalDateTime leaseUntil;

    @Column(length = 1000)
    private String lastError;
}
