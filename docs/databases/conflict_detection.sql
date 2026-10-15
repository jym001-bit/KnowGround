-- Only needed when Hibernate ddl-auto does not update the schema.
-- Run once after backing up the database; do not run on installations where these columns/tables already exist.
ALTER TABLE file_upload ADD COLUMN search_suppressed BOOLEAN NULL DEFAULT FALSE;
CREATE INDEX idx_file_upload_search_suppressed ON file_upload (search_suppressed);

CREATE TABLE document_conflicts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pair_key VARCHAR(64) NOT NULL,
    left_file_md5 VARCHAR(32) NOT NULL,
    left_chunk_id INT NOT NULL,
    right_file_md5 VARCHAR(32) NOT NULL,
    right_chunk_id INT NOT NULL,
    similarity DOUBLE NOT NULL,
    conflict_key VARCHAR(120) NOT NULL,
    left_value VARCHAR(500) NOT NULL,
    right_value VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(64) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NULL,
    CONSTRAINT uk_document_conflict_pair UNIQUE (pair_key)
);

CREATE TABLE conflict_detection_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    llm_confirmation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME NULL
);
