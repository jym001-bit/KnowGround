# 知据离线 RAG 评测

这套工具只在命令行运行，不修改线上数据，也不更改前端。每次检索策略、分块方式或模型变更后，用同一批问题和同一组人工标注的相关分块重新运行，比较 `Recall@K`、`MRR@K` 与回答得分。

下面命令假设 `python` 已加入 PATH，要求 Python 3.10 或更新版本；若 Windows 上提示找不到 `python`，把命令开头替换为已安装解释器的完整路径。

## 1. 准备固定数据集

保存为 JSONL（每行一个 JSON 对象），例如：

```jsonl
{"id":"config-01","question":"服务的默认端口是什么？","referenceAnswer":"默认端口为 8081。","expectedChunks":["文件MD5#分块序号"]}
```

`expectedChunks` 必须由人工核对；它是能够回答问题的**相关分块集合**，不是模型自己预测的结果。至少提供一个分块。`id` 在整批数据中唯一。数据集应固定版本并覆盖易错场景，例如跨分块、旧版本配置、同义表达和没有答案的问题；无答案问题需要另行设计指标，本工具要求每题至少有一条相关分块。

## 2. 收集真实检索排名

使用有权访问评测文档的测试账号登录后，将 JWT 放入当前终端的 `RAG_EVAL_TOKEN` 环境变量，不写入数据集或仓库。后端及依赖服务需要运行。

```powershell
$env:RAG_EVAL_TOKEN = '<测试账号的 JWT>'
python scripts/rag_eval.py collect --dataset eval/dataset.jsonl --out eval/retrieval.jsonl --top-k 5
```

`collect` 调用现有 `/api/v1/search/hybrid`，因此使用实际权限过滤、BM25/KNN、RRF 和 rerank。只保存每题的分块标识、模式和分数，不保存 JWT 或原文。若接口失败，命令直接报错，不把错误当成零召回。接口最多允许 `topK=20`。

## 3. 收集真实问答的答案

用相同账号在知据问答中逐题提问，复制**最终回答**到 `eval/answers.jsonl`，每行对应一个 `id`：

```jsonl
{"id":"config-01","answer":"默认端口是 8081。"}
```

当前问答使用 WebSocket/ReAct。人工收集最终回答能保留真实的工具选择、父子检索和提示词链路；评测脚本不会另造一个“看似相同”的生成链路。检索快照与问答应尽量在相同索引和配置下收集。不要把含敏感内容的评测数据提交到公开仓库。

## 4. 评委评分和归因

评委使用兼容 OpenAI Chat Completions 的 API。设置环境变量后运行：

```powershell
$env:RAG_EVAL_JUDGE_KEY = '<评委模型 API Key>'
$env:RAG_EVAL_JUDGE_URL = 'https://example.com/v1'
$env:RAG_EVAL_JUDGE_MODEL = '评委模型名'
python scripts/rag_eval.py evaluate --dataset eval/dataset.jsonl --retrieval eval/retrieval.jsonl --answers eval/answers.jsonl --out eval/report.json --top-k 5
```

- `Recall@K`：前 K 个去重结果中命中的相关分块数 ÷ 全部相关分块数；报告取题目宏平均。
- `MRR@K`：每题第一个相关分块排名的倒数，未命中记 0；报告取宏平均。
- 评委对照参考答案给 0～5 分，默认 3 分及以上算通过。低于 3 分且没有命中相关分块归因为“没搜到”；已命中则归因为“搜到了没用对”。这是定位线索，不能证明唯一根因。
- 评委返回格式不合法、API 调用失败或数据集 ID 不匹配会直接失败，避免生成误导性统计。报告带数据集 SHA-256，可核对两次比较是否用了同一批问题。
- 送给评委的包含题目、参考答案和实际回答；若其中有敏感内容，应使用获准处理这些数据的模型服务。

## 5. 人工抽样复核

```powershell
python scripts/rag_eval.py sample --report eval/report.json --out eval/reviews.jsonl --size 20 --seed 42
```

人工逐条阅读参考答案和实际回答，把每行的 `humanScore` 从 `null` 改成 0～5 的整数，填写可选的 `humanReason`。使用相同随机种子可复现抽样。再运行：

```powershell
python scripts/rag_eval.py review --report eval/report.json --reviews eval/reviews.jsonl
```

一致率是“评委与人工是否都判定通过”的相同次数 ÷ 已复核次数。低于 80% 时输出“评委需要重新校准”，命令退出码为 2，便于 CI 提醒。复核样本太少时结果波动较大，应结合具体分歧检查评委提示词、参考答案和评分阈值。

`scripts/test_rag_eval.py` 可用 `python -m unittest discover -s scripts -p test_rag_eval.py` 验证指标和复核逻辑。
