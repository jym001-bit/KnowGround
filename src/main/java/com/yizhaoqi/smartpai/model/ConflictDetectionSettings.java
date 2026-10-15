package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conflict_detection_settings")
public class ConflictDetectionSettings {
    @Id
    private Long id = 1L;

    @Column(name = "llm_confirmation_enabled", nullable = false)
    private boolean llmConfirmationEnabled;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
