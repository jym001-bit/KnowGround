package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "embedding_index_migration")
public class EmbeddingIndexMigration {
    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 128)
    private String targetIndex;

    @Column(nullable = false, length = 64)
    private String targetProvider;

    @Column(nullable = false, length = 128)
    private String targetModel;

    @Column(nullable = false)
    private int targetDimension;

    @Column(length = 128)
    private String previousIndex;

    @Column(length = 64)
    private String previousProvider;

    @Column(length = 128)
    private String previousModel;

    private Integer previousDimension;

    private long processedChunks;

    private long expectedChunks;

    @Column(length = 500)
    private String errorMessage;

    private LocalDateTime updatedAt;
}
