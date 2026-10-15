-- 在关闭旧版文件消费者、部署新版代码前执行；建议先备份 document_vectors。
-- 历史重复行保留每个 (file_md5, chunk_id) 中 vector_id 最大的一条。
DELETE older FROM document_vectors older
JOIN document_vectors newer
  ON older.file_md5 = newer.file_md5
 AND older.chunk_id = newer.chunk_id
 AND older.vector_id < newer.vector_id;

ALTER TABLE document_vectors
  ADD CONSTRAINT uk_document_vectors_file_chunk UNIQUE (file_md5, chunk_id);

CREATE TABLE IF NOT EXISTS file_task_outbox (
  id VARCHAR(36) PRIMARY KEY,
  file_md5 VARCHAR(32) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  lease_until DATETIME(6) NULL,
  last_error VARCHAR(1000) NULL,
  INDEX idx_file_task_outbox_due (status, next_attempt_at, lease_until)
);

CREATE TABLE IF NOT EXISTS file_task_execution (
  task_id VARCHAR(100) PRIMARY KEY,
  file_md5 VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  owner VARCHAR(36) NULL,
  lease_until DATETIME(6) NULL,
  last_error VARCHAR(1000) NULL
);

CREATE TABLE IF NOT EXISTS file_task_dead_letter (
  task_id VARCHAR(100) PRIMARY KEY,
  file_md5 VARCHAR(32) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  reason VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL
);
