# 每日 Token 赠送与个人用量

知据把 **Token 余额** 与 **个人每日硬上限** 分开管理。当前余额模式开启，个人每日硬上限关闭。普通用户的模型用量仍从总余额扣除；管理员免扣余额，但保留真实消费记录供本人查看。

## 发放规则

普通用户当天首次登录、读取自身资料或使用模型时，系统分别检查 LLM 和 Embedding：

| 类型 | 当天一次性加到现有余额 | 当天已消耗超过该值则跳过 |
| --- | ---: | ---: |
| LLM | 800,000 Token | 500,000 Token |
| Embedding | 500,000 Token | 300,000 Token |

阈值是严格的“超过”：恰好等于阈值仍可领取。两类互不影响；一次领取或跳过的结果在当天固定，之后重复登录和模型调用不会再次发放。未登录且没有模型调用的用户当天不会领取。管理员、系统任务不领取。

`UserTokenRecord` 当天的 `CONSUME` 记录用于判断已消耗量，Redis Lua 脚本在一个原子操作中检查当天赠送标记并增加余额。赠送成功后写入原因是“每日赠送”的余额记录。赠送标记保留三天；当天日期按运行中 JVM 的时区确定。Redis 余额和赠送标记必须一起备份与恢复，避免只恢复其中一项。数据库消费记录写入失败时应排查日志，因为阈值判断和个人用量会受影响。

## 查看用量

登录用户从 `/api/v1/users/usage` 查看自己的当天 LLM、Embedding Token 消耗和请求次数；普通用户同时看到现有余额。这个接口只根据当前登录身份查询，不能通过参数查看其他人的用量。个人中心的变动记录展示充值、初始赠送和每日赠送，以及实际消耗。管理员调用不扣余额，但会记入当天消耗。

## 配置与部署

使用 `USAGE_QUOTA_LLM_ENABLED=false` 和 `USAGE_QUOTA_EMBEDDING_ENABLED=false` 关闭个人每日硬上限；`USAGE_QUOTA_DAILY_GRANT_ENABLED=true` 开启每日赠送。赠送数量和跳过阈值使用同名前缀的 `LLM_TOKENS`、`EMBEDDING_TOKENS`、`LLM_SKIP_ABOVE`、`EMBEDDING_SKIP_ABOVE` 配置，完整键名见 `.env.example`。`USAGE_QUOTA_USE_USER_TOKEN_BALANCE=true` 必须保持开启。

上线时先备份云端 `.env.cloud`、后端 JAR、前端静态文件及 Redis/MySQL，再部署新代码和配置。恢复时需要同时回退代码与配置；若当天已领取赠送，不要只删除 Redis 赠送标记，否则再次访问可能重复领取。
