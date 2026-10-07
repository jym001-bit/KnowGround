# 知据 · KnowGround

![知据项目图标](frontend/public/favicon.svg)

知据是我在 PaiSmart 代码基础上学习和开发的 RAG 知识库项目。它把文档上传、解析、检索和对话串起来，让回答可以回溯到文档来源。仓库仍保留部分 `PaiSmart` / `smartpai` 命名，应用和云端部署也仍在完善；本文描述当前代码与已验证的运行状态。

![本地开发环境中的知据登录页](docs/images/login.png)

> 上图截自本地开发环境的 `/#/login`，不含账号和业务数据。云服务器上的管理面板与知据应用是两个独立系统；目前上线的是管理面板，知据应用尚未完成迁移。

## 导航

- [功能与页面](#功能与页面)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [本地开发](#本地开发)
- [项目结构](#项目结构)
- [云端部署与数据迁移](#云端部署与数据迁移)
- [验证与排障](#验证与排障)
- [项目来源](#项目来源)

## 功能与页面

| 功能 | 当前代码中的实现 | 主要入口 |
| --- | --- | --- |
| 登录与权限 | Spring Security、JWT；注册模式支持开放、邀请码和关闭 | `AuthController`、`UserController`、登录页 |
| 知识库 | 上传、文档管理、解析、分块、向量化和访问权限控制 | `UploadController`、`DocumentController`、知识库页 |
| 检索问答 | 混合检索后调用模型生成回答，传递引用映射 | `HybridSearchService`、`ChatHandler`、聊天页 |
| 对话历史 | 会话、消息和引用信息写入 MySQL | `ConversationService`、聊天历史页 |
| 组织与管理 | 组织标签、用户、邀请码、模型提供方、额度与用量页面 | 管理相关控制器及 `frontend/src/views/` |

这些功能能否完整使用，取决于数据库、队列、对象存储、检索服务和模型 API 是否配置正确。页面或类存在不等于整条业务链路已通过测试。

## 系统架构

### 服务关系

```mermaid
flowchart LR
    U[浏览器] --> F[Vue 3 前端]
    F -->|HTTP / WebSocket| B[Spring Boot 后端]
    B --> M[(MySQL<br/>用户、文档元数据、聊天历史)]
    B --> R[(Redis<br/>短期状态与上下文)]
    B --> K[Kafka<br/>文件处理任务]
    B --> O[(MinIO<br/>原始文件)]
    B --> E[(Elasticsearch<br/>文本与向量索引)]
    B --> D[DeepSeek API<br/>生成回答]
    B --> A[Embedding API<br/>生成向量]
    K --> W[后端消费者]
    W --> O
    W --> E
    W --> A
```

### 上传与提问的两条链路

```mermaid
flowchart TD
    UP[上传文件] --> STORE[MinIO 保存原文件]
    STORE --> QUEUE[Kafka 异步任务]
    QUEUE --> PARSE[解析、分块]
    PARSE --> EMB[Embedding 向量化]
    EMB --> INDEX[Elasticsearch 建索引]

    Q[用户提问] --> ACL[按用户与组织权限检索]
    ACL --> RETRIEVE[取回相关知识片段]
    RETRIEVE --> LLM[DeepSeek 生成回答]
    LLM --> REF[返回来源与引用预览]
    REF --> HISTORY[MySQL 保存会话]
```

**数据分工：**MySQL 保存需要长期保留的业务数据和聊天历史；MinIO 保存文件；Elasticsearch 保存可检索索引；Redis 用于短期状态，不应当作聊天历史的唯一存储。Kafka 承接异步任务，迁移前要确认待处理消息已消费完。

## 技术栈

| 分类 | 版本或组件 | 在项目中的作用 | 配置/代码依据 |
| --- | --- | --- | --- |
| 后端 | Java 17、Spring Boot 3.4.2、Maven | API、业务逻辑和 WebSocket | [`pom.xml`](pom.xml) |
| 前端 | Vue 3、TypeScript、Vite 6、Naive UI、Pinia、Vue Router、UnoCSS、pnpm | 页面与状态管理 | [`frontend/package.json`](frontend/package.json) |
| 数据库 | MySQL 8、Spring Data JPA | 用户、文档元数据、会话与记录 | [`application.yml`](src/main/resources/application.yml) |
| 缓存/消息 | Redis、Spring Kafka、Apache Kafka | 短期生成状态与异步文件处理 | 后端配置和消费者代码 |
| 搜索/文件 | Elasticsearch 8.10.x、MinIO | 混合检索、向量索引与原文件 | [`docs/docker-compose.yaml`](docs/docker-compose.yaml) |
| 文档解析 | Apache Tika、PDF 处理代码、LiteParse `lit` | 文本提取和 PDF 解析 | `DocumentService`、`ParseService` |
| AI | DeepSeek API、DashScope Embedding API | 回答生成与向量化 | `DeepSeekClient`、`EmbeddingClient` |
| 安全 | Spring Security、JWT | 身份认证与访问控制 | `SecurityConfig` |

部署镜像版本可能和 Maven 客户端依赖版本不同；以部署文件中实际镜像为准。Beszel 是独立的服务器监控软件，不参与 RAG 问答。

## 本地开发

### 环境要求

| 工具/服务 | 建议版本或说明 |
| --- | --- |
| JDK | 17 |
| Maven | 3.8.6 或更新 |
| Node.js / pnpm | Node.js 18.20+、pnpm 8.7+ |
| MySQL / Redis / Kafka / Elasticsearch / MinIO | 配置可用的本机或开发实例；Elasticsearch 示例镜像为 8.10.4 |
| 模型服务 | 有效的 DeepSeek 与 Embedding API Key；没有 Key 时无法验证完整问答链路 |
| PDF 解析 | 使用该功能时需让后端能执行 LiteParse 的 `lit` 命令 |

### 1. 准备基础设施

先确认 MySQL、Redis、Kafka、MinIO 和 Elasticsearch 可以从后端所在机器访问。仓库中的 [`docs/docker-compose.yaml`](docs/docker-compose.yaml) 是原有基础设施示例，带固定密码、对外映射端口和宿主机路径，**不能原样用于公网服务器**。本地使用也应先检查其端口、凭据、数据卷和 Elasticsearch 内存占用。

数据库名由 `SPRING_DATASOURCE_URL` 决定；示例文件中的库名和你本机现有库名可能不同，启动前保持一致。`docs/databases/ddl.sql` 可供查看原有表结构，但实际建表策略仍以当前 JPA 配置和迁移后的数据库状态为准。

### 2. 配置后端

从 [`.env.example`](.env.example) 复制一份为仓库根目录 `.env`。Windows PowerShell 可以执行：

```powershell
Copy-Item .env.example .env
```

Linux/macOS 可以执行：

```bash
cp .env.example .env
```

仓库中的 `DotenvEnvironmentPostProcessor` 会在从项目根目录启动后端时读取 `.env`。真实 `.env` 已被 `.gitignore` 排除，不能提交到仓库或发到聊天里。重点填写：

| 配置项 | 用途 | 常见问题 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 本地通常为 `dev` | 选错 profile 会读到不同默认值 |
| `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | MySQL 连接 | 库名、账号或权限不匹配 |
| `SPRING_DATA_REDIS_HOST`、`SPRING_KAFKA_BOOTSTRAP_SERVERS` | Redis 与 Kafka | 服务在虚拟机时不要误填 `localhost` |
| `MINIO_ENDPOINT`、`MINIO_PUBLIC_URL`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` | 原文件与下载地址 | 内外地址不一致可能影响签名 URL |
| `ELASTICSEARCH_HOST`、`ELASTICSEARCH_SCHEME`、`ELASTICSEARCH_PASSWORD` | 检索连接 | HTTP/HTTPS 和证书配置不一致 |
| `JWT_SECRET_KEY` | JWT 签名 | 必须提供有效的 Base64 密钥 |
| `DEEPSEEK_API_KEY`、`EMBEDDING_API_KEY` | 模型调用 | 额度、模型名、向量维度需匹配 |
| `FILE_PARSING_LITEPARSE_COMMAND` | PDF 解析命令 | 后端进程找不到 `lit` |

前端使用 [`frontend/.env.test`](frontend/.env.test) 和 [`frontend/.env.prod`](frontend/.env.prod) 分别配置开发/生产接口地址。`pnpm dev` 走 `test` 模式，默认连接 `http://localhost:8081/api/v1`。

### 3. 启动项目

后端通常由 IDE 直接运行；也可在仓库根目录运行：

```bash
mvn spring-boot:run
```

另开终端运行前端：

```bash
cd frontend
pnpm install
pnpm dev
```

| 地址 | 作用 |
| --- | --- |
| `http://localhost:9527/#/login` | 本地前端登录页 |
| `http://localhost:9527/#/chat` | 聊天页，需登录 |
| `http://localhost:9527/#/knowledge-base` | 知识库页，需登录 |
| `http://localhost:8081/api/v1` | 后端 API 前缀 |

登录后先做一次小文档上传与提问，验证 Kafka、MinIO、Embedding、Elasticsearch 和模型调用是否都通。只看到登录页不能说明 RAG 链路已正常。

### 日常开发命令

```bash
# 修改 Java 后优先编译，让已有的 IDE 热部署加载最新代码
mvn -q -DskipTests compile

# 需要验证后端行为时
mvn test

# 在 frontend 目录中执行
pnpm typecheck
pnpm build
```

本地开发时后端通常由 IDE 管理，不要仅为验证一处代码修改就先重启。前端开发服务器已运行时直接访问 `http://localhost:9527`。

## 项目结构

```text
KnowGround/
├── src/main/java/com/yizhaoqi/smartpai/  # 后端控制器、服务、消费者与模型客户端
├── src/main/resources/                 # application*.yml 与静态资源
├── frontend/src/views/                 # 聊天、历史、知识库和管理页面
├── frontend/packages/                  # 前端工作区模块
├── docs/                               # 数据库脚本和开发/部署记录
├── deployment/server-console/          # 已部署的独立 VPS 管理面板资料
├── .env.example                        # 本地配置样例
├── .env.cloud.example                  # 云端配置样例，尚需部署适配
├── pom.xml                              # Maven 依赖
└── README.md
```

| 配置文件 | 用途 |
| --- | --- |
| [`application.yml`](src/main/resources/application.yml) | 通用配置与知据应用的回答规则 |
| [`application-dev.yml`](src/main/resources/application-dev.yml) | 本地开发参数 |
| [`application-docker.yml`](src/main/resources/application-docker.yml) | 容器环境参数 |
| [`application-prod.yml`](src/main/resources/application-prod.yml) | 生产环境覆盖参数 |
| [`AGENTS.md`](AGENTS.md) | 本仓库内 Codex 的工作约定；不属于应用的系统提示词 |

## 云端部署与数据迁移

### 当前状态

| 项目 | 状态 |
| --- | --- |
| Ubuntu VPS、SSH、Docker、Docker Compose、HTTPS | 已准备 |
| 独立服务器管理面板与 Beszel 监控 | 已部署，见 [面板说明](deployment/server-console/README.md) |
| 知据应用的云端 Compose、前后端镜像 | **待完成** |
| MySQL、MinIO、Elasticsearch 的业务数据复制 | **待完成** |
| 云端登录、上传、问答、引用与历史的端到端验证 | **待完成** |

因此当前**没有一条可以直接运行并完成知据上线的 `docker compose up` 命令**。现有 [`docs/docker-compose.yaml`](docs/docker-compose.yaml) 主要是开发基础设施示例：密码写死、数据库端口暴露、Kafka 广播 `localhost`、Elasticsearch 使用 2 GB 堆。2 核 4 GB VPS 需要独立的云端 Compose、内存限制和安全配置。详情见 [云端部署准备记录](docs/cloud-deployment-plan.md)。

### 计划中的部署顺序

1. **盘点数据**：确认本机 MySQL、MinIO 和 Elasticsearch 的数据量及可访问状态，确定要迁移的库、桶和索引。Redis 只保存短期状态，不能用它替代 MySQL 聊天历史。
2. **做只读备份**：从本机导出 MySQL、复制 MinIO 对象；对 Elasticsearch 选择快照迁移或经验证的重建方式。保留本机原数据，并校验备份可读、大小和记录数。
3. **制作云端专用配置**：使用 [`.env.cloud.example`](.env.cloud.example) 填写独立的云端凭据；容器间使用服务名通信，数据库和中间件端口只在内网开放。真实 `.env.cloud` 不入库。
4. **构建镜像**：后端镜像包含 Java 运行环境及实际需要的 PDF 解析依赖；前端构建产物由 Web 服务提供。Elasticsearch 插件应与镜像版本匹配，避免每次启动临时下载。
5. **恢复并启动**：先启动数据库和中间件并恢复数据，再启动后端、前端及 HTTPS 代理；检查容器健康、日志、接口和磁盘/内存使用。
6. **验收与切换**：实测登录、上传 PDF、解析向量化、检索问答、引用预览和历史记录。确认云端与备份数据一致后再把访问入口切到云端。

迁移采用**复制**：本机数据保留。云端只包含备份时间点的数据；切换前本机新增的数据需要再次同步。35 GB 磁盘和 4 GB 内存对全套服务较紧张，部署前要按实际数据量核算。

### 需要提前验证的部署细节

| 风险点 | 为什么要验证 |
| --- | --- |
| MinIO 公网 URL 与签名下载 | 内网地址生成签名后直接替换域名可能造成下载 403 |
| LiteParse `lit` | 容器中缺少命令会导致 PDF 解析失败 |
| Elasticsearch IK 插件 | 插件版本必须与 ES 对应，启动时在线安装不稳定 |
| `/api/v1/test/**` 和 `/api/v1/internal/ocr/**` | 目前安全配置允许访问，上线前需限制公网入口 |
| 模型 API Key 与额度 | 云端应使用独立密钥并确认计费与限额 |

## 验证与排障

建议按“浏览器 → 后端 → 数据存储”逐层定位：

| 现象 | 优先检查 |
| --- | --- |
| 前端打不开 | `9527` 开发端口、Vite 进程、浏览器控制台 |
| 登录失败 | Network 中的响应、后端日志、MySQL 用户数据及 JWT 配置 |
| 上传后一直不完成 | MinIO 对象、Kafka topic/消费者、解析任务日志 |
| 聊天没有参考来源 | Elasticsearch 检索结果、权限过滤、引用映射与浏览器响应 |
| 历史记录为空 | 浏览器请求/响应、后端会话服务、MySQL 记录；不要只看 Redis |
| 云端网页正常但问答失败 | 容器健康、模型 API、Embedding API、ES、Kafka 和数据库连接 |

完整验收路径为“登录 → 上传 → 解析与向量化 → 检索问答 → 引用预览 → 历史记录”。前后端都要确认实际请求和持久化数据，不以页面能打开作为上线完成标准。

## 项目来源

知据在 PaiSmart 项目基础上继续学习和开发，保留了部分上游类名与目录结构。本 README 只描述本仓库的真实实现与状态，不引用原项目的商业推广、招聘成果或他人的截图。仓库根目录的 [`LICENSE`](LICENSE) 是 Apache License 2.0；前端依赖和其他第三方组件各自遵循其许可证。
