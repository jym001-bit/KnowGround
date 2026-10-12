# Embedding 模型升级：版本索引与回滚

当前检索、入库和 Agent 统计通过 ES 别名 `knowledge_base_current` 访问知识库。首次部署新代码时，启动器把该别名指向原有物理索引 `knowledge_base`；以后重启不会覆盖已切换的别名。旧索引不会自动删除。

如果生产环境的 `spring.jpa.hibernate.ddl-auto` 不是 `update`，部署新后端前先执行 [`databases/embedding_index_migration.sql`](databases/embedding_index_migration.sql)，再启动服务。

## 操作前

1. 在“模型配置”中保存并测试目标 Embedding provider 的 API 地址和密钥，先不要把它设为 active。同一 provider 升级模型时，在开始重建请求中填写 `targetModel`、`targetDimension`；普通配置保存会拒绝直接修改当前激活模型或维度。
2. 确认管理员 Embedding 额度足够。在线构建会为全库生成一次向量；追平会扫描 MySQL 与候选索引，仅对新增或变化分块重新生成向量，并删除已不存在的分块。首次回滚到旧版索引时，旧索引中的历史随机 ID 文档可能需要用旧模型重新生成一次。
3. 准备至少一组实际能命中的抽样问题及预期文件 MD5。切换前在候选索引上用目标模型做 KNN top 5 校验。建议覆盖不同组织和文档类型；权限过滤仍由正式检索路径执行，抽样校验只验证索引内容。
4. 当前消费暂停只覆盖本应用实例的 `fileProcessing` Kafka listener。多实例部署前应让其他实例停止消费该 topic，或者补充分布式暂停机制。重建期间不要修改目标 provider 的模型或维度。

## 管理员接口

所有接口都位于 `/api/v1/admin/embedding-index-migrations`，使用现有管理员 Bearer Token。

| 步骤 | 请求 | 结果 |
| --- | --- | --- |
| 开始后台构建 | `POST`，body: `{"targetProvider":"aliyun","targetModel":"目标模型名","targetDimension":2048}`；目标模型与维度可省略以使用该 provider 已保存的配置 | 创建 `knowledge_base_v...`，用目标模型分批向量化；原索引继续服务。 |
| 查看进度 | `GET` | `BUILDING`、`READY`、`FAILED`、`CATCHING_UP`、`SWITCHED`、`ROLLING_BACK` 或 `ROLLED_BACK`，以及文档数和错误原因。 |
| 验证并切换 | `POST /switch`，body: `{"probes":[{"question":"示例问题","expectedFileMd5":"文件MD5"}]}` | 暂停文件消费，仅补新／变更分块并清理已删分块，核对 MySQL 去重分块数与 ES 文档数、运行抽样问题，再用一条 ES 别名更新请求切换；查询请求在模型与别名切换的临界区等待。 |
| 回滚 | `POST /rollback` | 先用旧模型把保留的旧索引追平到当前文档，再核对数量并切回别名和旧模型，防止切换后新上传的文件在回滚时丢失。 |

切换和回滚都异步执行，使用 `GET` 轮询最终状态。失败时别名保持原目标，错误原因写入状态；Kafka 消费会恢复。构建或追平过程中服务重启后，未完成的任务标记失败；启动时会根据别名修正模型配置，避免索引与查询模型不配套。失败的候选索引保留供排查，不会自动删除。需要再次构建时重新调用开始接口。

问答检索在在线构建、追平和回滚期间继续使用当前别名。追平与回滚期间文件消费和直接删除／重建文档会等待，因此应在低峰操作；该等待时间取决于文档总量和模型服务速度。切换前没有管理员提交抽样问题时不会切换。
