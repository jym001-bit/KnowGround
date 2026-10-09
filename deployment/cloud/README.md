# 知据云端部署运行手册

> 云端主入口是 `https://jymcode.top/rag/`；纯 IP 入口继续可用。原 DuckDNS 服务器绑定已移除。本手册只描述云端实例；本机开发环境及原始数据仍保留。实际密码和模型密钥只在服务器 `/opt/knowground/.env.cloud`，该文件不入库。

## 当前结构

| 入口或服务 | 位置 | 说明 |
| --- | --- | --- |
| 知据新域名入口 | `https://jymcode.top/rag/` | Caddy 提供 `frontend/dist-rag` 静态文件；根路径留给以后其他项目 |
| 知据纯 IP 入口 | `https://23.252.124.29/` | 独立 IP 证书，同一套前端和后端；无域名 DNS 查询 |
| 知据 API / WebSocket | 新域名 `/rag/api/`、`/rag/proxy-ws/` | Caddy 去掉 `/rag` 前缀后转发到仅监听 `127.0.0.1:18081` 的后端 |
| 文件下载 | 新域名和纯 IP 入口均支持 `/rag/uploads/` | Caddy 转发到仅监听 `127.0.0.1:19000` 的 RustFS；只允许读取 |
| 服务器面板 | `https://jymcode.top/server/` | Authelia 认证与 Beszel 监控 |
| MySQL、Redis、Kafka、Elasticsearch | Docker 私有网络 | 不暴露到公网；RustFS 也只在本机回环地址映射 |

仓库中云端编排是 [`compose.yaml`](compose.yaml)，后端镜像见 [`Dockerfile.api`](Dockerfile.api)，Elasticsearch IK 镜像见 [`es/Dockerfile`](es/Dockerfile)，当前完整 Caddy 配置见 [`Caddyfile.example`](Caddyfile.example)，新域名 RAG 路由模块见 [`Caddyfile.rag-domain`](Caddyfile.rag-domain)，纯 IP 配置见 [`Caddyfile.ip-http`](Caddyfile.ip-http)、[`Caddyfile.ip-https`](Caddyfile.ip-https) 和 [`Caddyfile.ip-sni-prefix`](Caddyfile.ip-sni-prefix)。云端配置样例在仓库根目录 [`.env.cloud.example`](../../.env.cloud.example)。本地 `docs/docker-compose.yaml` 不能用于这台 VPS。

## 先理解配置与连接

本机从 IDE 启动后端时，项目的 `DotenvEnvironmentPostProcessor` 读取仓库根目录 `.env`；本机前端开发服务器读取 `frontend/.env` 和 `frontend/.env.test`。云端 Docker Compose 用 `--env-file .env.cloud` 和 `api.env_file` 把 `/opt/knowground/.env.cloud` 传给容器，后端启用 `docker,prod` profile；新域名的前端构建使用 `frontend/.env.rag`。**无需把 `.env` 改名为 `.env.cloud` 或互相覆盖。**

`compose.yaml` 是容器清单：`api` 通过 Docker 私有网络中的服务名 `mysql`、`redis`、`kafka`、`rustfs`、`es` 连接其他容器；容器里的 `localhost` 只代表该容器自身。数据库和搜索数据保存在 Docker 命名卷中，重新构建 `api` 镜像不会清空这些卷。Caddy 在容器外提供 HTTPS、前端静态页面，并把 API 请求转给后端。

纯 IP 地址另有短期 HTTPS 证书，由官方 Certbot 5.4 容器签发；`knowground-ip-cert.timer` 每天检查续期，再由 [`renew-ip-certificate.sh`](renew-ip-certificate.sh) 将证书安装给 Caddy 并重载。IP 证书约六天有效，务必保持这个定时器正常；Certbot 续期演练已成功。当前 `/etc/caddy/Caddyfile` 在原域名配置之外增加 IP 的 HTTP-01 验证与 HTTPS 路由，开头设置 `default_sni 23.252.124.29` 以支持浏览器直接访问 IP。原始 Caddyfile 备份是 `/etc/caddy/Caddyfile.pre-ip-20261007`。

后端的 `SECURITY_ALLOWED_ORIGINS` 允许新域名和 IP；`MINIO_PUBLIC_URL=/rag` 使新生成的文件预签名链接采用 `/rag/uploads/...` 相对路径，两个入口均有相应路由。已只读扫描云端 MySQL 文本列，旧 DuckDNS 域名引用数为 0；站外书签不在此检查范围内。纯 IP 访问仍走向同一台美国服务器，不会改善跨境链路延迟。

## 以后如何更新代码

以下命令在**本机 PowerShell**、仓库根目录执行。只有后端改动时跳过前端构建与上传；只有前端改动时跳过 JAR 构建、上传及 API 重建。这里上传的是构建产物，不需要把整个源码目录或本机 `.env` 上传。当前新域名的页面取自原线上版本；等你准备发布本机未提交的页面改动时，再执行前端构建。

```powershell
mvn -q '-Dmaven.test.skip=true' package
Push-Location frontend
node node_modules/vite/bin/vite.js build --mode rag --outDir dist-rag --configLoader runner
Pop-Location
tar -cf rag-dist.tar -C frontend/dist-rag .
scp -i "$env:USERPROFILE\.ssh\id_ed25519" target/SmartPAI-0.0.1-SNAPSHOT.jar root@23.252.124.29:/opt/knowground/target/SmartPAI-0.0.1-SNAPSHOT.jar
scp -i "$env:USERPROFILE\.ssh\id_ed25519" rag-dist.tar root@23.252.124.29:/tmp/rag-dist.tar
ssh -i "$env:USERPROFILE\.ssh\id_ed25519" root@23.252.124.29
```

登录后，**前端有改动**时先运行：

```bash
tar -xf /tmp/rag-dist.tar -C /opt/knowground/frontend/dist-rag
```

仅在**后端有改动**时运行：

```bash
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app up -d --no-deps --build api
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app ps
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app logs --tail=80 api
```

前端文件由 Caddy 直接读取，上传后刷新 `https://jymcode.top/rag/` 即可；缓存未更新时可强制刷新。若改了 `compose.yaml`、Dockerfile 或 Caddy 配置，先单独核对并同步相应部署文件，再重建对应服务；上面上传 JAR/前端的流程不会自动同步这些文件。若改动涉及数据库表结构或数据迁移，更新前先做云端备份。不要运行 `docker compose down -v`，它会删除数据库等命名卷。

## 日常查看

SSH 登录后运行：

```bash
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app ps
bash deployment/cloud/verify-data.sh
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app logs --tail=100 api
free -m
df -h /
```

改云端参数只修改 `/opt/knowground/.env.cloud`，然后仅重建受影响的容器，例如后端：

```bash
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app up -d --no-deps --force-recreate api
```

不要把 `.env.cloud` 输出到聊天或提交到 Git。迁移来的 `model_provider_configs` 有密文，所以 `MODEL_PROVIDER_SECURITY_SECRET_KEY` 必须沿用原环境的加密密钥；`JWT_SECRET_KEY` 使用云端独立的 32 字节 Base64 密钥。

## 本次数据迁移记录

| 项目 | 云端核对结果 |
| --- | ---: |
| MySQL 表 | 15 |
| 用户 / 文件 / 对话 | 2 / 3 / 20 |
| MySQL 文档分块 | 587 |
| RustFS 对象 | 3，共 71,998,719 字节 |
| Elasticsearch `knowledge_base` | 587 条索引文档 |

本机只读备份位于被 Git 忽略的 `.runtime/backups/`；云端副本在 `/opt/knowground/backups/`。MySQL 通过 [`restore-mysql.sh`](restore-mysql.sh) 导入，文件通过 [`restore-storage.py`](restore-storage.py) 导入并核对大小；从公网预签名地址下载真实文件的 SHA-256 校验由 [`verify-public-storage.py`](verify-public-storage.py) 完成。迁移没有删除本机 MySQL、MinIO 或 Elasticsearch 数据。备份之后本机新增的内容不会自动同步。

本机 Elasticsearch 许可证过期，原索引无法直接读取；云端使用 `cloud-reindex` 一次性 profile 从已迁移的 MySQL 文本分块重新调用 Embedding API，重建 3 份文档共 587 个向量。这个过程消耗模型用量。需要重建单个文件时，先备份云端数据，再停止未对外服务的 API 或安排维护窗口，使用：

```bash
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app run --rm --no-deps \
  -e SPRING_PROFILES_ACTIVE=docker,prod,cloud-reindex \
  -e SERVER_PORT=0 \
  -e CLOUD_REINDEX_CONFIRM=true \
  -e CLOUD_REINDEX_FILE_MD5=<文件MD5> api
```

重建器代码在 [`CloudReindexRunner.java`](../../src/main/java/com/yizhaoqi/smartpai/CloudReindexRunner.java)；它只删除并重建所选文件在**云端** Elasticsearch 的索引，不改本机数据，也不重新解析 PDF。`CLOUD_REINDEX_FILE_MD5=all` 会处理所有文件并再次消耗模型额度；不要随意重复运行。

## 回退与待验收事项

切换前的 Caddy 配置保存在 `/etc/caddy/Caddyfile.pre-knowground-20261007`。若首页上线导致问题，可在服务器上恢复旧入口：

```bash
cp /etc/caddy/Caddyfile.pre-knowground-20261007 /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy
```

该操作只回退公网路由；不会删除知据容器或迁移数据。当前已验证容器健康、两个首页 HTTP 200、原面板认证跳转、测试 API 被 Caddy 拦截、登录 API 可达、两个入口真实文件签名下载与 SHA-256。手机端已确认原账号登录、旧文档和聊天历史可见；IP 入口已确认旧文档预览/下载正常；最近一次问答的回答与引用映射已写入 MySQL；[`verify-liteparse.sh`](verify-liteparse.sh) 在 896 MB 内存限制下解析真实 PDF 成功。仍需在浏览器确认**问答显示、引用点开预览、PDF 上传和 Kafka 异步解析**，并在实际访问高峰观察 4 GB 内存、swap 和磁盘。不能只凭健康探针判断所有功能正常。
