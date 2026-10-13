package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Blob;

/**
 * 文档向量实体类
 * 用于存储文本分块和相关元数据
 */
@Data
@Entity
@Table(name = "document_vectors", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_vectors_file_chunk", columnNames = {"file_md5", "chunk_id"}))
public class DocumentVector {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vectorId;

    @Column(name = "file_md5", nullable = false, length = 32)
    private String fileMd5;

    @Column(name = "chunk_id", nullable = false)
    private Integer chunkId;

    @Lob
    private String textContent;

    @Column(name = "chunk_context", length = 1000)
    private String chunkContext;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "anchor_text", length = 512)
    private String anchorText;

    @Column(length = 32)
    private String modelVersion;
    
    /**
     * 上传用户ID
     */
    @Column(nullable = false, name = "user_id", length = 64)
    private String userId;
    
    /**
     * 文件所属组织标签
     */
    @Column(name = "org_tag", length = 50)
    private String orgTag;
    
    /**
     * 文件是否公开
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;
}
