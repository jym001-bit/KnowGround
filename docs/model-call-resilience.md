# 模型调用容错与请求预算

## 调用路径

在线问答在 WebSocket 收到问题时生成 120 秒截止时间。ReAct 的决策回合、工具检索、Embedding、BM25/KNN 召回和 rerank 共用该时间；决策与工具阶段在剩余 30 秒时停止，最后一次 LLM 请求不携带工具定义。后台任务没有问答截止时间，使用各调用自己的超时配置。

LLM 和 Embedding 请求经过 `ModelCallGuard`：先进入有界等待队列，从 Redis Lua 令牌桶取得令牌，再由 Resilience4j TimeLimiter 和 CircuitBreaker 保护供应商请求。仅 408、429、5xx、网络错误和超时可重试；每次重试按指数退避加随机抖动，且等待时间不短于响应中的 `Retry-After`。每个供应商每分钟的重试次数不超过原始调用次数的 10%。

LLM 流式调用只有在尚未向用户输出正文时才会切换到已启用、已配置 API Key 的备用供应商。已经输出正文时直接报告错误，避免把两个模型的答案拼在一起。Embedding 不自动切换模型：不同模型的向量不能与现有 Elasticsearch 索引混用；需要使用版本索引重建与别名切换流程。

## 令牌桶与优先级

Redis 脚本 `redis/token_bucket.lua` 原子更新总桶和后台桶。后台桶按供应商配置 QPS 的 70% 补充令牌；在线请求只消耗总桶，因此后台向量化不会长期占满在线调用额度。在线等待最多 5 秒，后台等待最多 60 秒；超过等待队列容量立即拒绝。注册、登录、聊天请求的旧 `INCR + EXPIRE` 固定窗口也改用同一个原子脚本。

## 配置

`application.yml` 的 `model-calls.llm` 和 `model-calls.embedding` 提供默认 QPS、超时、熔断阈值、重试次数和等待队列容量。可以按供应商覆盖，例如：

```yaml
model-calls:
  llm:
    providers:
      deepseek:
        qps: 8
        timeout-millis: 60000
        failure-rate-percent: 50
        minimum-calls: 10
        open-seconds: 30
```

备用供应商必须先在“模型配置”中启用并配置有效 API Key。Redis 不可用时，令牌桶不会放行模型调用，以免失去供应商 QPS 保护。

## 默认值与配置边界

| 参数 | LLM 默认 | Embedding 默认 | 作用 |
| --- | ---: | ---: | --- |
| `qps` | 5 | 10 | 每个 `scope:provider` 的 Redis 总桶补充速度 |
| `timeout-millis` | 90000 | 30000 | 单次模型尝试的超时上限；有请求截止时间时取较短者 |
| `minimum-calls` | 10 | 10 | 熔断器开始按失败率判断前的最少调用数 |
| `failure-rate-percent` | 50 | 50 | 熔断阈值 |
| `open-seconds` | 30 | 30 | 熔断保持打开的时长 |
| `max-retries` | 2 | 2 | 单次原始调用最多计划重试次数，仍受全局重试预算约束 |
| `queue-capacity` | 100 | 100 | 每个本机供应商/优先级等待通道的容量 |

`ModelCallProperties` 支持给供应商覆盖 QPS、超时和熔断参数；`max-retries` 与 `queue-capacity` 当前只按 LLM/Embedding 类别配置。熔断器、等待队列和“每分钟重试不超过原始调用 10%”计数都保存在**应用进程内存**，多实例之间不共享；Redis 令牌桶才是跨实例共享的 QPS 边界。单个供应商可能同时提供 LLM 与 Embedding，它们目前使用不同的 `scope:provider` 桶，配置时需要把两个类别的额度一起计入供应商合同上限。

`max-retries=2` 表示最多两次重试的上界，不保证每次失败都会重试。预算不足、剩余截止时间不够、熔断已打开、用户已看到流式内容或遇到非临时性错误时会直接停止。`Retry-After` 是最短退避时间，若长于请求剩余预算则不会等待后再重试。

## 120 秒 Agent 预算如何传递

`ChatHandler` 在收到 WebSocket 问题时记下单调时钟截止时间，并传给异步 ReAct 工作线程。最多执行四轮决策和八次工具调用；决策、检索、Embedding 和 rerank 共用剩余时间。工具阶段只使用“总截止时间减去 30 秒”的工作预算，剩余不足时跳过后续工具和 rerank，最后一轮不携带工具定义，请模型直接作答。每次阻塞等待和模型调用都按“自身配置超时、请求剩余时间”的较小值执行。

这是一条**应用侧预算**：网络关闭、线程调度、持久化及客户端渲染仍可能产生少量额外时间，不能把 120 秒理解成浏览器端严格的硬实时保证。后台上传没有这条问答截止时间，只受后台等待上限和模型单次超时约束。

## 排查顺序

1. 请求立即被拒：检查等待队列是否满、Redis 是否可达、该供应商的 QPS 是否配置过小。
2. 请求等待约 5 秒或 60 秒后失败：检查在线/后台优先级、Redis 令牌桶和供应商是否被持续占用。
3. 主模型失败却没有切换：检查备用 provider 是否启用并配置了 API Key，以及主模型是否已经输出过正文。
4. 搜索返回 RRF 顺序：检查 rerank 的独立密钥、模型接口和 Agent 剩余时间；rerank 不经过这里的 LLM/Embedding 熔断器。
5. 更换 Embedding 模型后结果异常：检查当前 ES 别名、模型和向量维度是否匹配；按[版本索引迁移流程](embedding-index-migration.md)重建，不要在原索引混写不同模型的向量。

当前文档描述的是代码行为；真实恢复和故障切换仍需在可用 Redis、模型服务和浏览器问答路径下演练。
