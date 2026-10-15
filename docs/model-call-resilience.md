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
