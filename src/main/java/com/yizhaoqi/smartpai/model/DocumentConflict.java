package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "document_conflicts", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_conflict_pair", columnNames = "pair_key"))
public class DocumentConflict {
    public static final String PENDING = "PENDING";
    public static final String KEEP_LEFT = "KEEP_LEFT";
    public static final String KEEP_RIGHT = "KEEP_RIGHT";
    public static final String DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pair_key", nullable = false, length = 64)
    private String pairKey;
    @Column(name = "left_file_md5", nullable = false, length = 32)
    private String leftFileMd5;
    @Column(name = "left_chunk_id", nullable = false)
    private Integer leftChunkId;
    @Column(name = "right_file_md5", nullable = false, length = 32)
    private String rightFileMd5;
    @Column(name = "right_chunk_id", nullable = false)
    private Integer rightChunkId;
    @Column(name = "similarity", nullable = false)
    private double similarity;
    @Column(name = "conflict_key", nullable = false, length = 120)
    private String conflictKey;
    @Column(name = "left_value", nullable = false, length = 500)
    private String leftValue;
    @Column(name = "right_value", nullable = false, length = 500)
    private String rightValue;
    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDING;
    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
