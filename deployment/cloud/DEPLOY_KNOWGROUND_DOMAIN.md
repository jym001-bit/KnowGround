# 知据切换到独立子域名

当前入口：`https://knowground.jymcode.top/`。`jymcode.top` 保留 `/server/` 和 `/authelia/` 等管理入口，不再提供 `/rag/` 项目页面。2026-10-09 已完成服务器切换；下文保留重部署和回退步骤。

## 已知配置

- DNS：`knowground.jymcode.top` 的 A 记录指向 `23.252.124.29`。上线前仍须在服务器或外部网络核对解析。
- 后端 Compose 入口：`deployment/cloud/compose.yaml`，API 映射 `127.0.0.1:18081:8081`，RustFS 映射 `127.0.0.1:19000:9000`。
- 前端采用根路径生产配置 `frontend/.env.prod`，构建目录为 `frontend/dist-knowground`。
- 目标 Caddy 示例见 `deployment/cloud/Caddyfile.example`；若服务器已经有在线配置，使用 `prepare-knowground-caddy.py` 从在线配置生成候选文件。脚本只删除主域名旧 `/rag/` 路由并加入 `Caddyfile.knowground-domain`，保留管理入口及 IP 路由。

## 上线前检查

先从可 SSH 登录服务器的终端运行以下只读命令，确认线上状态。不要仅凭仓库里的历史部署说明判断服务器已经运行：

```bash
getent ahostsv4 knowground.jymcode.top
sudo systemctl is-active caddy
sudo ss -lntp | grep -E ':(80|443|18081|19000)\b'
test -f /opt/knowground/deployment/cloud/compose.yaml && echo compose-present
test -f /opt/knowground/.env.cloud && echo cloud-env-present
```

如果 Docker、Caddy 或 `/opt/knowground` 尚不存在，先按实际服务器系统安装并初始化基础服务，配置 `.env.cloud` 中的密码和模型密钥，再启动 Compose；不要把本机 `.env` 上传到服务器。数据库已有数据时，先备份 MySQL、对象存储和 Elasticsearch；切换域名本身不要求清空数据卷。

## 本机构建

在仓库根目录的 PowerShell 中运行：

```powershell
mvn -q test
mvn -q '-Dmaven.test.skip=true' package
Push-Location frontend
pnpm typecheck
node node_modules/vite/bin/vite.js build --mode prod --outDir dist-knowground --configLoader runner
Pop-Location
tar -cf knowground-dist.tar -C frontend/dist-knowground .
```

构建后的 `index.html` 应引用根路径 `/assets/...`，不能再引用 `/rag/assets/...`。前端 API 为同源 `/api/v1`，WebSocket 为 `/proxy-ws/chat/...`。

## 同步与切换

以下以仓库里记录的服务器 IP 和原有 SSH 用户为例；先确认服务器实际地址及登录方式。如果服务器已有 `/opt/knowground`，上传构建产物以及 `prepare-knowground-caddy.py`、`Caddyfile.knowground-domain`，然后在服务器运行：

```bash
python3 /opt/knowground/deployment/cloud/prepare-knowground-caddy.py /etc/caddy/Caddyfile /tmp/Caddyfile.knowground-candidate
diff -u /etc/caddy/Caddyfile /tmp/Caddyfile.knowground-candidate
```

检查差异仅移除主域名旧 `/rag/` 路由并加入新站点，尤其确认 `/server/`、Authelia、证书和 IP 路由原样保留。脚本只生成候选文件，遇到与预期不同的线上配置会中止。**不要直接用仓库样例覆盖尚未核对的线上 Caddyfile。**

服务器 `.env.cloud` 应设置：

```dotenv
MINIO_PUBLIC_URL=same-origin
SECURITY_ALLOWED_ORIGINS=https://23.252.124.29,https://knowground.jymcode.top
```

`same-origin` 使新生成的文件链接采用 `/uploads/...`，Caddy 新站点把该路径转发给 RustFS。更改 `.env.cloud` 后仅重建 API 容器；其他数据容器保留。新前端文件放到 `/opt/knowground/frontend/dist-knowground`。

在服务器将已核对的候选 Caddyfile 写入单独文件后执行：

```bash
sudo caddy validate --config /tmp/Caddyfile.knowground-candidate
sudo cp -p /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-knowground-domain
sudo cp /tmp/Caddyfile.knowground-candidate /etc/caddy/Caddyfile
sudo systemctl reload caddy
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app up -d --no-deps --build api
```

如 Caddy 验证或重载失败，恢复备份并重新加载；不要删除 Docker 命名卷。服务器防火墙和云平台安全组需允许公网访问 80、443，Caddy 才能为新域名申请证书。

## 验收

```bash
curl -I https://knowground.jymcode.top/
curl -I https://jymcode.top/server/
curl -I https://jymcode.top/rag/
```

预期新站点首页可达，主域名管理入口仍可用，主域名 `/rag/` 返回 404。然后在浏览器实际验证登录、提问、WebSocket 回复、引用预览、文档上传和下载。若新域名出现 502，先检查 API 容器和 Caddy 日志；若证书未签发，检查 DNS、80/443 入站及 Caddy 日志。

2026-10-09 切换后已验证：新域名 HTTPS 首页与静态资源返回 200，证书校验通过；登录 POST 可达（空请求返回 400）；测试接口返回 404，RustFS 写入路径返回 405；主域名 `/rag/` 返回 404，`/server/` 仍跳转至认证；真实文件经新域名签名下载返回 200 且 SHA-256 一致。API 和基础设施容器健康。仍需使用真实账号在浏览器完成问答、引用预览和上传的业务验收。

服务器切换前备份位于 `/opt/knowground/backups/domain-switch-20261009/`，含旧 Caddyfile、旧 `.env.cloud`、旧 JAR 和 MySQL 导出。MySQL 导出已用 `--no-tablespaces` 重做并校验完成；不要使用首次因 `PROCESS` 权限不足而产生的不完整导出。
