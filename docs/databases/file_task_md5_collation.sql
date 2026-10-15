-- MySQL 8.0: file_upload.file_md5 默认使用 utf8mb4_0900_ai_ci，
-- 旧的任务表可能使用 utf8mb4_unicode_ci。跨表比较前统一这两列。
-- 执行前先通过 information_schema.columns 核对 file_upload.file_md5 的排序规则，
-- 并备份 file_task_execution、file_task_outbox。
ALTER TABLE file_task_execution
  MODIFY COLUMN file_md5 VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;

ALTER TABLE file_task_outbox
  MODIFY COLUMN file_md5 VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
