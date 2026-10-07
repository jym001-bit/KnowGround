# 知据 RAG 核心真实测试

测试代码是 `ZhijuRagCoreLiveIT.java`，只保留必要的 RAG 核心检查：

1. 未登录不能读取知识库。
2. 正样本批量检索，计算 Recall@1、Recall@3、Precision@3、MRR 和 P95 耗时。
3. 无答案问题测试误召回率。

这里的“相关片段”必须同时满足：来自人工预先指定的正确文档，并包含人工预先指定的答案关键词。Recall@K 表示多少比例的问题在前 K 条中至少找到一个相关片段；Precision@K 表示返回片段中相关片段的比例；MRR 越高，说明正确片段排得越靠前。

## 运行准备

1. 启动本地后端。
2. 默认测试集依据当前账号已经索引的 `面经.pdf`、`面渣逆袭MySQL篇V2.2.pdf` 和 `面渣逆袭Java基础篇V2.1.pdf` 编写。更换账号或知识库后，需要同步修改测试集。
3. 检查 `src/test/resources/rag/retrieval-cases.json` 中的问题、正确文档和答案关键词。标准答案必须在运行前人工确定。
4. 直接运行时，测试会读取项目 `.env` 中的本地管理员账号完成登录。也可以在 IDEA 的 Run Configuration → Environment variables 中配置专用测试账号的 `ZHIJU_TEST_TOKEN`（登录 JWT，不含 `Bearer ` 前缀），或配置 `ZHIJU_TEST_USERNAME` 和 `ZHIJU_TEST_PASSWORD`。显式测试配置优先于 `.env`。
5. 打开 `ZhijuRagCoreLiveIT.java`，点击类或单个 `@Test` 旁的绿色按钮。

可选环境变量：`ZHIJU_TEST_BASE` 修改服务地址，默认 `http://localhost:8081`；`ZHIJU_RAG_DATASET` 使用另一份测试集。

命令行运行：

```powershell
mvn "-Dtest=ZhijuRagCoreLiveIT" test
```

真实结果保存在 `target/zhiju-rag-results`。每道正样本会记录返回排名、文件、片段、分数、相关性判断和耗时，适合直接整理进报告。

当前测试集只有5个正样本和2个负样本，用于先跑通流程。正式课堂实验建议扩充到至少30个正样本和10个负样本，并按直接问法、口语改写、近义词、跨段信息分别统计。只有一份相关文档时，Precision@3天然不容易达到1，这是指标定义造成的，不是程序错误。

`ZhijuRagToolTest` 是离线单元测试；`ZhijuRagCoreLiveIT` 才是实际访问 Elasticsearch 和向量服务的真实检索评估。真实测试不会调用聊天大模型，因此不产生回答记录，但会调用查询向量服务并消耗相应额度。
