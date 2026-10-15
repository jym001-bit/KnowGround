# RAG 检索与上下文技术说明

本文对应当前 `master` 中的 `HybridSearchService`、`RrfFusion`、`ChunkContextService`、`ParentContextExpander` 和 `RerankClient`。搜索接口返回的小分块是证据与引用单位；交给 LLM 的上下文可以更大。修改检索策略时，先用[固定题集](rag-evaluation.md)记录基线。

## 1. 文档入库与索引文本

1. 解析器把文件内容切成有序小分块，MySQL `document_vectors` 保存原文、文件 MD5、分块号、页码以及可选的 `chunk_context`。
2. `knowledge.contextual-retrieval.enabled` 默认 `false`。开启后，`ChunkContextService` 按分块号每 **8** 块提交一批，请 LLM 为待处理小分块各写一条简短上下文说明。一次请求只带本批及邻近分块，窗口默认最多 **6000 字符**；单条说明默认最多 **200 字符**。已有说明的分块不重复生成；某批失败时该批仍以原文入索引。
3. `ContextualChunkText.compose` 把“说明 + 空行 + 原文”作为 Embedding 输入。ES 保留独立的 `chunkContext` 和 `textContent` 字段，展示与引用仍使用原文。开启上下文检索时，BM25 使用 `combined_fields` 对这两个字段匹配，rerank 也读取相同的组合文本。
4. 已经入库的旧分块不会仅因打开开关就自动补全说明；要让旧数据获得相同效果，需要安排重新生成说明与重建向量/索引。更换 Embedding 模型则按[版本索引迁移流程](embedding-index-migration.md)操作。

这些限制按**字符数**执行，不是精确的 Token 上限。LLM 的实际计费仍取决于模型分词方式和响应长度。

## 2. 查询、融合与重排

```text
问题 → 查询 Embedding ─┬→ ES KNN：向量相关性 ───┐
                      └→ ES BM25：关键词匹配 ───┴→ RRF 排名融合
                                                   ↓
                                         最多 50 条候选 rerank
                                                   ↓
                                      阈值过滤、权限复核、返回 topK
```

- BM25 和 KNN 在独立执行器中并行召回。每路窗口默认 `topK × 30`，但最多 **300**；KNN 的 `num_candidates` 另按窗口乘数计算。召回等待上限默认 **10 秒**，问答请求有更短剩余时间时取较短者。
- RRF 对每个分块的两路**排名**求和：`1 / (k + BM25名次) + 1 / (k + KNN名次)`，默认 `k=60`。没有进入某一路的结果不加该路得分。它不直接相加 BM25 分数与向量分数，避免量纲不同造成不稳定权重。
- 默认将融合后最多 **50** 条候选交给 `gte-rerank-v2`。rerank 分数达到默认阈值 `0.2` 的结果才返回，`topK` 是上限，实际条数可能更少。请求需有效 API Key：单独的 `SEARCH_RERANK_API_KEY`，或已激活的阿里云 Embedding provider 的 Key。
- rerank 关闭、调用失败、超时或问答剩余时间低于默认 **2 秒**时，直接按 RRF 顺序返回；此时 `retrievalMode=HYBRID_RRF`，分数是 RRF 分数，不能与 rerank 的 `HYBRID` 分数直接比较。查询向量生成失败时尝试 `TEXT_ONLY` 纯文本检索。

核心配置在 `application.yml` 的 `search.hybrid`：`recall-multiplier`、`max-recall-window`、`rrf-k`、`recall-timeout-seconds` 及 `rerank.*`。**默认配置不等于真实外部模型可调用**；需要检查当前密钥、网络和后端响应。

## 3. 权限、冲突与引用

BM25 和 KNN 查询都带文件访问预过滤：上传者本人、公开文件或用户有效组织标签匹配。组织标签包括上级继承后的有效标签。管理员审核中被停用检索的文件 MD5 在 ES 查询中排除；结果返回前再次依据 MySQL 文件状态和权限复核。权限解析失败时不放行结果。

Agent 的知识库工具在命中小分块后，`ParentContextExpander` 从**同一文件**取相邻分块，默认前后各最多 **1** 块；单个扩展块最多 **1500 字符**，本次搜索扩展总量最多 **6000 字符**。重叠的父块会复用；预算用尽时剩余命中仍保留小分块。引用编号、页码和预览锚点仍指向最初命中的小分块，不把邻居冒充成命中证据。

## 4. 验证与排查

| 现象 | 首先核对 |
| --- | --- |
| 明明有关键词却搜不到 | 文档处理状态、ES 别名、BM25 字段映射、用户/组织权限、冲突停用状态 |
| 只看到 `HYBRID_RRF` | rerank 开关、密钥、网络、超时和 Agent 剩余时间；这表示兜底，不表示代码没有 rerank |
| 只看到 `TEXT_ONLY` | 查询 Embedding 的 provider、额度、熔断和超时 |
| 分块离开原文读不懂 | 上下文说明是否开启、旧数据是否重建、父块扩展是否开启、字符预算是否耗尽 |
| 调整后效果难判断 | 用同一题集、同一 K 和相同可访问范围跑 `scripts/rag_eval.py`，比较 Recall@K、MRR@K 与答案质量 |

纯单元测试只能验证排序与边界逻辑。完整验收要在真实 ES、对象存储、模型 API 和有权限的测试账号下检查搜索结果及引用。
