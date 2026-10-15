# 文件异步处理可靠性升级

上传合并成功后，`FileTaskOutboxService` 在同一 MySQL 事务中提交文件完成状态和待投递消息。`FileTaskOutboxDispatcher` 每秒扫描待投递消息；Kafka 确认后标为 `SENT`，失败按指数退避和随机抖动重试。崩溃发生在 Kafka 确认与 MySQL 标记之间时可能重复投递，因此消费端仍必须幂等。

消费者以消息 `taskId` 为主键认领 `file_task_execution`。正在处理的任务有租约，每 30 秒续约；已完成的重复消息直接跳过，被其他消费者持有的任务等待重试，租约过期后可重新认领。失败时允许 Kafka 重试；重试耗尽的消息落入 DLT，另一个监听器将其保存至 `file_task_dead_letter` 并记录错误日志供告警系统采集。旧消息缺少 `taskId` 时按文件和任务类型生成稳定 ID。

解析失败后重试会删除上次留下的该文件分块与 ES 文档，再完整解析。`document_vectors` 对 `(file_md5, chunk_id)` 加唯一约束，ES 文档 ID 也固定为 `fileMd5:chunkId`，防止重复行和重复 ES 文档。

## 部署顺序

1. 暂停旧版文件消费者，备份 MySQL，执行 [`databases/file_processing_outbox.sql`](databases/file_processing_outbox.sql)。脚本会清理历史重复分块；如已有唯一索引，应跳过对应 `ALTER TABLE`。
2. 发布新版代码，恢复文件消费者，观察 `file_task_outbox` 是否从 `PENDING` 变为 `SENT`，`file_task_execution` 是否变为 `COMPLETED`。
3. 管理员可调用 `GET /api/v1/admin/file-tasks/dead-letters` 查看死信记录；将 `文件处理死信告警` 错误日志接入现有告警通道。

模型 API 已成功返回但进程尚未写入 ES 时，进程崩溃后重试仍可能再次调用模型。这属于跨 MySQL、Kafka、ES 和外部模型服务无法共同提交事务的窗口；本升级保证重复 Kafka 投递不会并发重复处理，已完成任务不会再调用模型。事务外的 MinIO 对象写入同样不能与 MySQL 原子提交。

## 超时任务恢复与历史任务隔离

`FileTaskClaimService` 为正在处理的任务保存 owner、两分钟租约和 `heartbeat_at`，每 **30 秒**续约。消费者在处理流程及分块批次边界调用 `FileTaskHeartbeat.pulse()`；查询 Embedding 不写文件任务心跳。`FileTaskRecoveryService` 每 **1 分钟**扫描一次，找出心跳超过 **10 分钟**未更新的 `PROCESSING` 任务。它用带状态和时间条件的 SQL 更新抢占任务，避免多个实例同时恢复；成功后把对应已发送的 Outbox 事件重新设为 `PENDING`，由投递器重发。

`recovery_count` 达到 **3** 时任务置为 `EXHAUSTED`、文件向量化状态置为 `FAILED`，并写 `file_task_dead_letter` 供管理员查看和告警。找不到可重投的 Outbox 事件时也置失败，不盲目构造消息。启动时，旧版缺少心跳的 `PROCESSING` 任务会批量置失败，不重新灌入 Kafka；缺少 task ID 的旧 Kafka 消息被消费端快速跳过并把对应文件标记失败。这避免历史积压任务长时间占住新版消费线程，旧任务需要人工检查后按正常上传或重建流程重试。

余额不足等明确不可恢复错误应直接失败，避免重试风暴；网络和服务暂时不可用才适合重试。是否真的停止重试，应结合当时抛出的异常类型和 Kafka 错误处理器日志核对，不能只看文件最终显示 `FAILED`。

`AbandonedUploadCleanupService` 每 **1 小时**检查一次超过 **24 小时**没有新分片的未完成上传，每轮最多处理 50 条。清理前以状态条件更新抢占；同一 MD5 被多个文件记录引用时不删共享分片。删除对象存储分片、分块记录和 Redis bitmap 后保留文件记录，用户仍可重新上传。

部署这一阶段前，如果没有开启 Hibernate 自动建表，先备份 `file_task_execution` 和 `file_upload` 并执行 [`databases/file_task_recovery.sql`](databases/file_task_recovery.sql)。升级后观察 `heartbeat_at`、`recovery_count`、Outbox 状态、死信表及新上传的消费耗时；旧任务被快速标记失败是预期行为，需要单独核查其原始文件。
