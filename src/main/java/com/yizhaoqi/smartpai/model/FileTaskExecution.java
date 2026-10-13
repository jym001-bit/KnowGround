package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_task_execution")
@Getter
@Setter
public class FileTaskExecution {
    @Id
    @Column(length = 100)
    private String taskId;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 36)
    private String owner;

    private LocalDateTime leaseUntil;

    @Column(length = 1000)
    private String lastError;
}
