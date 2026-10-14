-- 在部署第 7 阶段代码前执行；先备份 file_task_execution 与 file_upload。
-- 本脚本建立进度心跳、恢复次数和上传分片活动时间。
ALTER TABLE file_task_execution
  ADD COLUMN heartbeat_at DATETIME(6) NULL,
  ADD COLUMN recovery_count INT NOT NULL DEFAULT 0,
  ADD INDEX idx_file_task_execution_heartbeat (status, heartbeat_at);

ALTER TABLE file_upload
  ADD COLUMN last_chunk_at DATETIME(6) NULL,
  ADD INDEX idx_file_upload_abandoned (status, last_chunk_at);

-- 升级前没有分片时间，给历史未完成上传重新计 24 小时，防止误清理。
UPDATE file_upload SET last_chunk_at = NOW(6)
WHERE status = 0 AND last_chunk_at IS NULL;
