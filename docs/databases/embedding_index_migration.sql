-- Apply before deploying the migration service when Hibernate ddl-auto is not update.
CREATE TABLE IF NOT EXISTS embedding_index_migration (
    id BIGINT NOT NULL PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    target_index VARCHAR(128) NOT NULL,
    target_provider VARCHAR(64) NOT NULL,
    target_model VARCHAR(128) NOT NULL,
    target_dimension INT NOT NULL,
    previous_index VARCHAR(128) NULL,
    previous_provider VARCHAR(64) NULL,
    previous_model VARCHAR(128) NULL,
    previous_dimension INT NULL,
    processed_chunks BIGINT NOT NULL DEFAULT 0,
    expected_chunks BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    updated_at DATETIME(6) NULL
);
