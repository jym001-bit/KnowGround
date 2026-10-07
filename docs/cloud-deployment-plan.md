# 知据单机云部署准备记录（2026-10-06）

## 推荐规格

预算约 100 元/年时，优先尝试 **Oracle Cloud Always Free A1：2 OCPU / 12 GB ARM**。免费配额和库存以账号控制台为准；A1 是 ARM，必须核对所有容器镜像、Elasticsearch IK 插件和 LiteParse 的 ARM 兼容性。免费实例可能因资源不足无法创建，闲置实例也可能被回收，必须另做备份。此方案无需购买一年期 VPS，但不能保证申请成功或稳定可用。

预算 **100–300 元/年**可以找到小商家的 4 GB 境外 VPS，但需要压缩各容器内存，且在下单前核对库存、续费价和线路。2026-10-06 查到 BitsFlowCloud 官方秋季活动页有美国盐湖城 FCF26-SLC-M（2 vCPU / 4 GB / 40 GB NVMe，229 元年付），以及美国洛杉矶 FCF26-LAX-L（4 vCPU / 4 GB / 80 GB NVMe，269 元年付）。后者的磁盘容量更接近本项目需要；两者都尚未经过本项目实机验证。活动页：https://bitsflow.cloud/26-aut-zz.html 。此前仅按 RackNerd 常规特价推断“300 元内找不到 4 GB”不准确，已修正。阿里云或腾讯云的超低首年促销必须在结算页确认地域，不能把中国内地促销价当作免备案境外价格。

如果要求付费、整套服务单机且尽量少改架构，预算需提高到至少 8 GB 内存档位。面向国内访问、暂不做 ICP 备案，可选中国香港节点。当前项目要同时运行 Java、MySQL、Redis、Kafka、MinIO、Elasticsearch 和前端代理；建议从 **2 核 8 GB / 80 GB SSD** 起步，文档较多或多人同时使用时选 **4 核 8 GB**。`docs/docker-compose.yaml` 中 Elasticsearch 堆已经设置为 2 GB，因此 1 GB / 2 GB / 4 GB 内存套餐不适合原架构整套同机部署。

选购时核对：Linux x86_64、固定公网 IPv4、磁盘扩容方式、月流量和超量费用、首年和续费价格、跨境网络质量、快照费用。价格以登录后的结算页为准。

## 当前准备状态

- 根目录 `.env` 用于 IDE 本地开发；复制 `.env.cloud.example` 为云服务器上的 `.env.cloud`，填入独立凭据。Compose 可通过 `--env-file .env.cloud` 注入变量，后端 Spring 使用 `docker,prod` profile。
- `application.yml` 的 MinIO 与 Elasticsearch 地址/凭据已改为环境变量占位符，避免本地地址覆盖云配置。
- 现有 `docs/docker-compose.yaml` 是开发基础设施示例，带固定密码、宿主机绝对路径、对外开放数据库端口、Kafka 广播 `localhost`、ES 运行时下载 IK 插件，不能直接当公开服务器的生产 Compose。
- 前端生产 API 已设为同源 `/api/v1`，WebSocket 客户端走 `/proxy-ws/chat/...`；反向代理需要同时转发两个路径。

## 上线前待完成

1. 新建云端 Compose 和镜像：前端 Nginx、Java 后端、MySQL、Redis、Kafka、MinIO、Elasticsearch。内网服务不映射公网端口；仅开放 80/443 和受限 SSH。固定镜像版本，设置数据卷、健康检查、资源限制。ES 镜像预装与其版本匹配的 IK 插件。
2. 后端镜像必须安装 LiteParse 的 `lit` 命令；PDF 解析依赖它。确认中文 OCR 语言包或保持 OCR 关闭。校验文件上传、解析、向量化、检索整条链路。
3. 设计 MinIO 对外地址和签名 URL：代码当前先用内网 endpoint 生成签名、再直接替换为 publicUrl。签名包含 Host，替换后可能导致浏览器下载 403；上线前要修正并实测。建议准备两个域名：站点域名和文件域名，统一使用 HTTPS。
4. 在反向代理禁止 `/api/v1/test/` 与 `/api/v1/internal/ocr/` 的公网访问，后端也应增加生产环境限制。`SecurityConfig` 当前放行这两个路径。
5. 从本地迁移 MySQL 数据、MinIO 对象和 Elasticsearch 索引，记录备份与恢复步骤。Kafka 队列先消费完毕，Redis 临时会话无需作为持久历史迁移；聊天历史应以 MySQL 为准。
6. 更换云端独立的数据库、Redis、MinIO、ES、JWT 和模型 API 凭据。不要把本地 `.env`、真实 `.env.cloud` 或私钥上传到仓库。
7. 云端按“登录 → 上传 PDF → 完成向量化 → 问答 → 引用预览 → 历史记录”实测，并确认浏览器 Network、后端日志、MySQL 和 MinIO 对象。

## 本机验证结果

- `mvn -q -DskipTests compile`：通过。
- `pnpm typecheck`：失败，`frontend/playwright-kb-column.spec.ts` 缺少 `@playwright/test`；不涉及本次环境变量修改。
- `pnpm build`：本机 Windows 对 `node_modules/.vite-temp` 和生成的 `src/typings/auto-imports.d.ts` 返回 EPERM，生产构建未验证。
- 本机无 Docker 命令，9527 和 8081 未监听；不能在本机确认容器和浏览器运行效果。
