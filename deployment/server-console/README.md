# VPS 管理面板

当前部署在 GreenCloud VPS `23.252.124.29`，系统为 Ubuntu 24.04。此目录保存不含密码的配置样本及部署辅助脚本；实际密码、密钥和运行数据只存放在 VPS 上。

## 访问

- 管理面板：<https://jym-debug.duckdns.org/server/>
- 监控入口：管理面板中的“监控”，或 <https://jym-debug.duckdns.org/server/#monitor>
- 登录账号：`admin`
- 初始密码：从自己的电脑 SSH 登录 VPS 后，在 VPS 终端运行 `sed -n 's/^Random Password: //p' /root/server-console-initial-credentials.txt`。不要把输出发送给别人。

浏览器旧会话若仍显示 Beszel 登录页，可以清除该域名的网站数据后重新登录；无痕窗口已验证可直接进入监控主页。

## 运行结构

| 服务 | 用途 | 监听 |
| --- | --- | --- |
| Caddy | HTTPS 与统一入口 | 公开 80、443 |
| Authelia | 独立网页登录 | 127.0.0.1:9091 |
| server-console | 命令与服务操作面板 | Unix socket `/run/server-console/http.sock` |
| Beszel Hub | 监控网页和指标存储 | 127.0.0.1:8090 |
| Beszel Agent | 采集本机指标 | 127.0.0.1:45876 |
| SSH | 服务器管理 | 公开 22，使用本机私钥 |

所有网页入口都经过 Caddy 和 Authelia。Beszel Hub 只接受 Caddy 注入的可信用户标识，并有本机出站访问规则限制其他用户访问其回环端口。监控中的 `jym-vps` 已登记为本机，状态 `up`，已开始写入指标。

## 日常检查

在 VPS 上运行：

```bash
systemctl status caddy docker server-console beszel-hub beszel-agent beszel-guard
python3 /root/server-console-deploy/verify-beszel.py
```

无密码的配置文件位于 `/etc/caddy/Caddyfile`、`/etc/server-console/config.json` 和 systemd 服务文件。Authelia 配置、用户密码哈希和 Beszel 环境文件含密钥，只应由 root 读取。面板源码位于 `/opt/server-console`，Hub 数据位于 `/var/lib/beszel-hub`。

当前完成的是服务器管理面板。PaiSmart/RAG 应用和其 MySQL、Redis、Elasticsearch、Kafka、MinIO 尚未迁移到 VPS。面板中“重启服务”目前指向 `server-console.service`；部署应用后应把 `piUnit` 和健康检查改为实际应用服务。
