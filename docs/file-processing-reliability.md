# 文件异步处理可靠性升级

上传合并成功后，`FileTaskOutboxService` 在同一 MySQL 事务中提交文件完成状态和待投递消息。`FileTaskOutboxDispatcher` 每秒扫描待投递消息；Kafka 确认后标为 `SENT`，失败按指数退避和随机抖动重试。崩溃发生在 Kafka 确认与 MySQL 标记之间时可能重复投递，因此消费端仍必须幂等。

消费者以消息 `taskId` 为主键认领 `file_task_execution`。正在处理的任务有租约，每 30 秒续约；已完成的重复消息直接跳过，被其他消费者持有的任务等待重试，租约过期后可重新认领。失败时允许 Kafka 重试；重试耗尽的消息落入 DLT，另一个监听器将其保存至 `file_task_dead_letter` 并记录错误日志供告警系统采集。旧消息缺少 `taskId` 时按文件和任务类型生成稳定 ID。

解析失败后重试会删除上次留下的该文件分块与 ES 文档，再完整解析。`document_vectors` 对 `(file_md5, chunk_id)` 加唯一约束，ES 文档 ID 也固定为 `fileMd5:chunkId`，防止重复行和重复 ES 文档。

## 部署顺序

1. 暂停旧版文件消费者，备份 MySQL，执行 [`databases/file_processing_outbox.sql`](databases/file_processing_outbox.sql)。脚本会清理历史重复分块；如已有唯一索引，应跳过对应 `ALTER TABLE`。
2. 发布新版代码，恢复文件消费者，观察 `file_task_outbox` 是否从 `PENDING` 变为 `SENT`，`file_task_execution` 是否变为 `COMPLETED`。
3. 管理员可调用 `GET /api/v1/admin/file-tasks/dead-letters` 查看死信记录；将 `文件处理死信告警` 错误日志接入现有告警通道。

模型 API 已成功返回但进程尚未写入 ES 时，进程崩溃后重试仍可能再次调用模型。这属于跨 MySQL、Kafka、ES 和外部模型服务无法共同提交事务的窗口；本升级保证重复 Kafka 投递不会并发重复处理，已完成任务不会再调用模型。事务外的 MinIO 对象写入同样不能与 MySQL 原子提交。
