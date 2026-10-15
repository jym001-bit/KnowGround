# 知据技术文档导航

以下文档按当前仓库 `master` 整理。`master` 上有实现代码，不代表本机或云端已部署、模型密钥有效或旧数据已迁移；这些需要分别验证。根目录 [`README.md`](../README.md) 适合先了解功能和启动方法。

| 想了解什么 | 从哪里读 | 关键代码或脚本 |
| --- | --- | --- |
| BM25/KNN、RRF、rerank、上下文说明、父子检索 | [RAG 检索技术说明](rag-retrieval.md) | `HybridSearchService`、`ChunkContextService`、`ParentContextExpander` |
| 如何用数据比较检索和回答 | [离线 RAG 评测](rag-evaluation.md) | `scripts/rag_eval.py` |
| 冲突候选、LLM 确认、管理员选择保留文档 | [文档冲突检测](document-conflict-detection.md) | `DocumentConflictService` |
| 更换 Embedding 模型时重建和回滚索引 | [索引迁移与回滚](embedding-index-migration.md) | `EmbeddingIndexMigrationService` |
| Kafka 重复投递、Outbox、心跳和废弃上传 | [文件异步处理可靠性](file-processing-reliability.md) | `FileTaskOutboxDispatcher`、`FileTaskRecoveryService` |
| 模型超时、熔断、令牌桶和 Agent 总预算 | [模型调用容错](model-call-resilience.md) | `ModelCallGuard`、`ModelCallQueue`、`ChatHandler` |
| 公网部署与回退 | [云端部署说明](../deployment/cloud/README.md) | `deployment/cloud/` |

`chunking-optimization-plan.md` 是设计计划，`paismart.md` 是历史资料；需要判断“现在是否实现”时，以当前代码和上表的现状文档为准。

## 数据库迁移清单

先确认目标库、后端版本及现有表结构，备份数据，再按实际缺失项迁移。`src/main/resources/application*.yml` 默认 `ddl-auto=update`，它可能在应用启动时新增表或列；已有数据的去重、唯一约束和升级顺序仍需人工核对。生产环境如果使用 `validate` 或禁用自动建表，应提前执行相应脚本。**不要把以下脚本全部无条件重跑**，其中有不带 `IF NOT EXISTS` 的 `ALTER TABLE`。

| 功能 | 脚本 | 执行条件与注意事项 |
| --- | --- | --- |
| 冲突检测 | [`conflict_detection.sql`](databases/conflict_detection.sql) | 首次安装冲突检测且表/列不存在时；先备份 `file_upload` |
| 冲突 LLM 确认开关 | [`conflict_confirmation_settings.sql`](databases/conflict_confirmation_settings.sql) | 已有冲突检测但缺少设置表时 |
| Embedding 索引迁移状态 | [`embedding_index_migration.sql`](databases/embedding_index_migration.sql) | 缺少迁移状态表时；ES 别名另由服务管理 |
| Outbox 与消费幂等 | [`file_processing_outbox.sql`](databases/file_processing_outbox.sql) | 关闭旧消费者后执行；先备份 `document_vectors`，脚本会删历史重复行并加唯一约束 |
| 心跳恢复与废弃上传 | [`file_task_recovery.sql`](databases/file_task_recovery.sql) | Outbox 阶段之后、缺少心跳列时；先备份 `file_task_execution` 与 `file_upload` |

第 8～10 项模型容错、Redis Lua 限流和请求截止时间不新增 MySQL 表，但需要 Redis 可访问、模型配置正确。迁移后用数据库表结构、服务日志和实际上传/问答路径分别验收；仅看到表名或通过编译不足以证明链路可用。
