# CLAUDE.md：知据项目开发说明

本文件供 Claude Code 在知据（KnowGround）仓库中使用。先遵循 [`AGENTS.md`](AGENTS.md) 的协作和验证约定，再参考本文的项目入口。用户可见名称是“知据”，现存的 `smartpai` 包名和部分类名属于历史运行标识。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java/com/yizhaoqi/smartpai/` | Spring Boot 后端：控制器、服务、仓储、Kafka 消费者、模型客户端等 |
| `src/main/resources/application*.yml` | 后端本地、开发和 Docker 环境配置 |
| `frontend/src/` | Vue 3 + TypeScript 前端：页面、路由、状态、接口和组件 |
| `frontend/.env*`、`frontend/vite.config.ts` | 前端环境变量与代理配置 |
| `docs/` | 设计、迁移和部署资料；阅读具体文件前核对其是否适用于当前环境 |
| `README.md` | 项目功能、技术栈、开发与部署现状的入口 |

主要链路是：文档上传到 MinIO，经 Kafka 异步解析与切分，调用 Embedding 服务向量化并写入 Elasticsearch；提问时检索相关内容，再调用大模型生成带引用的回答。MySQL 保存业务与持久化对话数据，Redis 保存缓存和短期上下文。

## 本地开发

后端通常由 IDE 运行并热加载。修改 Java 代码后先编译，不要默认重启：

```bash
mvn -q -DskipTests compile
```

前端通常已在 `http://localhost:9527` 运行。需要自己启动时，在 `frontend` 目录执行 `pnpm install`、`pnpm dev`。需要验证时使用 `pnpm typecheck`，或只对改动文件运行 ESLint。相关服务地址与凭据以实际 `.env` 和 `application*.yml` 为准，避免把真实密钥输出到日志或文档。

排查页面问题时，检查真实浏览器中的请求、响应和控制台。排查聊天历史时，核对 MySQL 持久化记录与 Redis 短期状态；排查知识库问题时，再检查 MinIO、Kafka、Elasticsearch 和引用映射。

## 部署状态

当前部署步骤与未完成事项以 [`docs/cloud-deployment-plan.md`](docs/cloud-deployment-plan.md) 和 [`README.md`](README.md) 为准。`docs/docker-compose.yaml` 是历史依赖服务配置，不能仅凭 `docker-compose up -d` 就认定知据应用已完成生产部署。云端部署需要独立配置、数据复制与恢复、服务健康检查、HTTPS 和完整业务验收；迁移本机数据时保留原数据。

不要把示例密码当作生产凭据，也不要将本地或云端真实 `.env`、API Key、数据库口令提交到仓库。
