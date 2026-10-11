-- Use when conflict detection is already installed and Hibernate ddl-auto is not update.
CREATE TABLE conflict_detection_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    llm_confirmation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME NULL
);
