# VernFlow 可插拔 RAG 调研报告

## 1. 调研目的

本报告用于回答 4 个问题：

1. VernFlow 当前的 RAG 实现处于什么阶段。
2. 业界常见的开源 RAG 方案和论文路线分别是什么。
3. 如果要把 VernFlow 做成可插拔 RAG，哪些方案适合直接接入，哪些更适合做实验分支。
4. 后续若要实现随机游走和子模覆盖算法，这些 RAG 方案的兼容性如何。

## 2. 当前项目现状

### 2.1 当前实现

当前仓库中的 RAG 是一套轻量即时检索实现：

- 后端先按“工作文件 / 全局工作区”收集范围文件并抽文本、切 chunk。
- AI 服务对本次请求内的 chunk 临时构建 TF-IDF 表示。
- 使用 cosine similarity 做 chunk 排序。
- assistant 走 `retrieve + generate`。
- workbench 的 retriever 节点走 `retrieve only`。

对应代码：

- [vernflow-backend/src/main/java/com/vernflow/modules/assistant/service/WorkspaceDocumentScopeService.java](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/vernflow-backend/src/main/java/com/vernflow/modules/assistant/service/WorkspaceDocumentScopeService.java>)
- [vernflow-backend/src/main/java/com/vernflow/integration/ai/AiPaperClient.java](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/vernflow-backend/src/main/java/com/vernflow/integration/ai/AiPaperClient.java>)
- [vernflow-ai/app/modules/rag/service.py](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/vernflow-ai/app/modules/rag/service.py>)

### 2.2 当前实现的问题

1. 每次检索都会重新抽文本、切块、建 TF-IDF，缺少持久化索引。
2. 没有 embedding、向量索引、混合检索、重排器。
3. 没有检索器注册表，无法方便地横向比较不同 RAG 策略。
4. retrieval 和 selector 没有真正分层，不利于后续接入随机游走和子模覆盖。

### 2.3 文档中已经体现的未来方向

前端原型已经预留了：

- Retriever: `Dense TopK / Hybrid`
- Evidence Selector: `Coverage / MMR / TopK`

见：

- [docs/前端原型/vernflow_frontend_v3.html](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/docs/前端原型/vernflow_frontend_v3.html>)

这说明项目目标本身就是“可切换检索器 + 可切换证据选择器”，当前代码还没完全走到这一步。

## 3. 外部调研结论

### 3.1 基础 RAG 路线

#### RAG 原始论文

- Lewis et al., 2020, *Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks*  
  链接：[arXiv](https://arxiv.org/abs/2005.11401)

价值：

- 奠定了“非参数检索记忆 + 生成模型”的基本范式。
- 适合作为 VernFlow 的概念基线。

局限：

- 并不直接告诉我们如何做工程上的“可插拔”。

#### Dense Retrieval

- Karpukhin et al., 2020, *Dense Passage Retrieval for Open-Domain Question Answering*  
  链接：[arXiv](https://arxiv.org/abs/2004.04906)

价值：

- 提供 dense retriever 的基础路线。
- 很适合作为 VernFlow 后续 dense baseline。

局限：

- 只是 retriever，不覆盖 selector、reranker、graph traversal。

### 3.2 证据去冗余与覆盖选择

#### MMR

- Carbonell and Goldstein, 1998, *The Use of MMR, Diversity-Based Reranking for Reordering Documents and Producing Summaries*  
  链接：[PDF](https://www.cs.cmu.edu/~jgc/publication/The_Use_MMR_Diversity_Based_LTMIR_1998.pdf)

价值：

- 非常适合作为 VernFlow 的第一版 evidence selector。
- 直接对应原型里的 `MMR` 选项。

局限：

- 本质是多样性重排，不是图搜索，也不是全局覆盖优化。

#### 子模覆盖

- Lin and Bilmes, 2011, *A Class of Submodular Functions for Document Summarization*  
  链接：[ACL / PDF](https://aclanthology.org/P11-1052/)

价值：

- 很适合做“覆盖率优先”的证据选择器。
- 对“少量证据覆盖尽可能多主题/断言”非常契合。

局限：

- 需要先有候选集合，再在候选集合上做选择。
- 因此它更适合作为 selector 层，而不是 retriever 层。

### 3.3 图结构 / 随机游走路线

#### GraphRAG

- Microsoft Research, *From Local to Global: A Graph RAG Approach to Query-Focused Summarization*  
  链接：[arXiv](https://arxiv.org/abs/2404.16130)  
  项目页：[Microsoft Research](https://www.microsoft.com/en-us/research/project/graphrag/?msockid=1c49064acceb626a1c4e1095cd6763a8)  
  GitHub：[microsoft/graphrag](https://github.com/microsoft/graphrag)

价值：

- 适合回答“全局主题 / 数据集级总结”问题。
- 图索引天然与随机游走、多跳检索兼容。

局限：

- 构图与索引成本高。
- 不适合直接作为 VernFlow 第一版默认 RAG。
- 更适合后续研究分支。

#### LightRAG

- HKUDS, *LightRAG: Simple and Fast Retrieval-Augmented Generation*  
  链接：[arXiv](https://arxiv.org/abs/2410.05779)  
  GitHub：[HKUDS/LightRAG](https://github.com/HKUDS/LightRAG)

价值：

- 图结构和向量检索做了折中。
- 支持多种查询模式，适合做 GraphRAG 轻量对照组。

局限：

- 更像独立框架，不像小组件。
- 要作为 VernFlow 内核直接嵌入，改造成本不低。

#### HippoRAG

- *HippoRAG: Neurobiologically Inspired Long-Term Memory for Large Language Models*  
  链接：[arXiv](https://arxiv.org/abs/2405.14831)

价值：

- 典型的“图遍历 / 关联式检索”思路。
- 对未来随机游走很有参考价值。

局限：

- 更偏研究型系统，直接即插即用难度较高。

### 3.4 开源工程框架

#### Haystack

- 官方文档：[Retrievers](https://docs.haystack.deepset.ai/v2.9/docs/retrievers)

优点：

- 组件化非常清晰，retriever、reranker、generator 都是组件。
- 对“替换 retriever、替换 document store、接入 pgvector”很友好。
- 很适合做服务端可插拔原型。

缺点：

- 更像重建一套 pipeline，不是最轻量的嵌入方式。

判断：

- 适合作为架构参考。
- 不建议整套迁入 VernFlow 主链。

#### LlamaIndex

- 官方文档：[RetrieverQueryEngine / BaseRetriever](https://developers.llamaindex.ai/python/framework-api-reference/query_engine/retriever/)

优点：

- retriever、node postprocessor、query engine 分层清楚。
- 非常适合“retriever + selector + postprocessor”实验。

缺点：

- 更适合快速研究，不一定适合直接作为长期生产内核。

判断：

- 适合作为 selector / postprocessor 设计参考。
- 也适合用来快速验证子模覆盖或 MMR 方案。

#### LangChain

- 官方文档：[Retriever integrations](https://docs.langchain.com/oss/python/integrations/retrievers/index)

优点：

- retriever 接口统一，集成生态大。
- 适合快速接多种外部后端。

缺点：

- 组件很多，但研究型可控性不如自己分层。

判断：

- 适合作为接口风格参考。
- 不建议把 VernFlow 的核心 RAG 绑死在 LangChain 上。

#### FlashRAG

- 论文：[FlashRAG: A Modular Toolkit for Efficient Retrieval-Augmented Generation Research](https://arxiv.org/abs/2405.13576)

优点：

- 明确定位为研究复现和横向比较工具。
- 模块化强，适合实验。

缺点：

- 更偏 research toolkit，不是面向现有产品服务的嵌入式组件。

判断：

- 很适合作为“实验对照方法仓”的参考。
- 不适合作为 VernFlow 主线用户服务的直接运行内核。

### 3.5 向量与混合检索基础设施

#### pgvector

- 官方仓库：[pgvector/pgvector](https://github.com/pgvector/pgvector)

优点：

- 你们已经有 PostgreSQL + pgvector 环境。
- 最适合作为 VernFlow 的第一版向量索引底座。
- 数据、权限、用户隔离都能继续复用现有数据库体系。

缺点：

- 只是向量存储和检索基础，不包含上层 selector、graph traversal。

判断：

- 高优先级推荐。

#### ColBERT / Late Interaction

- 原始论文入口：[ColBERT](https://papers.cool/arxiv/2004.12832)

优点：

- 检索质量通常优于简单单向量 dense。
- 非常适合作为高质量对照组。

缺点：

- 工程复杂度和资源成本高于普通 dense。
- 不适合第一阶段主线默认方案。

判断：

- 适合作为第二阶段实验型可插拔方案。

## 4. 适合 VernFlow 的可插拔 RAG 分层

建议把 RAG 明确拆成 5 层，而不是只替换一个检索函数：

1. `DocumentParser`
- 文本抽取
- OCR / OpenXML / 代码 / Markdown

2. `Chunker`
- chunk 长度
- overlap
- 层级切块

3. `Retriever`
- TF-IDF
- BM25 / PostgreSQL FTS
- Dense / pgvector
- Hybrid
- Graph-based retriever

4. `Selector`
- TopK
- MMR
- Coverage
- Submodular coverage
- Random walk re-ranking

5. `Generator`
- DeepSeek
- 其他模型

## 5. 哪些方案适合“即插即用”

### 5.1 可以优先即插即用的

#### A. pgvector Dense Retriever

推荐度：高

原因：

- 现有数据库已具备条件。
- 与当前用户隔离、工作区隔离模型天然兼容。
- 工程改造量可控。

#### B. PostgreSQL FTS / BM25 风格 Sparse Retriever

推荐度：高

原因：

- 可以和 pgvector 一起做 hybrid。
- 适合作为对照 baseline。
- 比当前每次现算 TF-IDF 更像正式检索系统。

#### C. Hybrid Retriever

推荐度：高

原因：

- 最贴合原型里的 `Hybrid`。
- 也最适合后续做横向对比。

实现建议：

- sparse + dense 各取 top-k
- 用 RRF 或加权融合

#### D. MMR Selector

推荐度：高

原因：

- 成本低
- 效果直观
- 与 workbench、assistant 都容易对接

### 5.2 适合第二阶段接入的

#### E. Submodular Coverage Selector

推荐度：高，但第二阶段

原因：

- 非常适合 VernFlow 的“证据覆盖选择”语义。
- 但前提是先有稳定候选集合和统一打分接口。

#### F. ColBERT / Late Interaction Retriever

推荐度：中高

原因：

- 适合做高质量实验对照。
- 但实现和部署复杂度高于 dense + hybrid。

#### G. GraphRAG / LightRAG / HippoRAG

推荐度：中

原因：

- 适合多跳、全局主题、结构化关系检索。
- 和随机游走高度兼容。
- 但更适合作为研究分支，不适合立刻替代主线 RAG。

## 6. 与随机游走、子模覆盖的兼容性分析

### 6.1 随机游走

随机游走不应该直接绑在“向量检索器”内部，更适合放在：

- 图构建后
- 或候选召回后
- 作为 graph reranker / traversal selector

兼容性结论：

- `TF-IDF / Dense / Hybrid`：可作为候选召回层，与随机游走兼容。
- `GraphRAG / LightRAG / HippoRAG`：天然兼容，尤其适合多跳和结构化扩散。
- `ColBERT`：本身不是图方法，但可作为随机游走前的高质量候选召回器。

### 6.2 子模覆盖

子模覆盖更像 selector，不像 retriever。

适合放在：

- 初始召回之后
- 最终送给 LLM 之前

兼容性结论：

- `TF-IDF / Dense / Hybrid`：都兼容，因为它们都能先给出候选集合。
- `MMR`：可作为轻量 baseline。
- `Submodular coverage`：可作为更强 selector。
- `GraphRAG / LightRAG`：若先输出候选节点/路径，也兼容，但实现更复杂。

## 7. 对 VernFlow 的推荐路线

### 第一阶段：先把主线做成真正可插拔

目标：

- 不换整套框架
- 先把当前代码抽象成可替换组件

建议：

1. 保留现有 `assistant` / `workbench` 调用方式。
2. 在 `vernflow-ai` 内部抽出：
   - `Retriever`
   - `Selector`
   - `Generator`
3. 先实现 4 个 profile：
   - `tfidf-topk`
   - `sparse-fts-topk`
   - `dense-pgvector-topk`
   - `hybrid-rrf`
4. Selector 先实现：
   - `topk`
   - `mmr`

### 第二阶段：实验能力增强

建议：

1. 加 `submodular-coverage`
2. 加 `random-walk-rerank`
3. 视实验需求引入：
   - `colbert`
   - `lightrag`
   - `graphrag`

### 第三阶段：研究分支

适合做：

- corpus 级主题总结
- 跨文档关系发现
- 多跳链路证据拼接

候选：

- GraphRAG
- LightRAG
- HippoRAG

## 8. 最终建议

### 不建议

1. 直接把 VernFlow 主线整体迁到某个外部 RAG 框架。
2. 现在就把 LightRAG / GraphRAG 当默认方案。
3. 把随机游走和子模覆盖硬塞进 retriever 层。

### 建议

1. 以 `pgvector + PostgreSQL FTS + Hybrid` 作为主线可插拔底座。
2. 把 `selector` 从 `retriever` 中独立出来。
3. 把 `random walk` 和 `submodular coverage` 明确定位到 selector / reranker 层。
4. 将 `GraphRAG / LightRAG / HippoRAG` 作为实验分支，而不是第一版生产默认路径。

## 9. 对当前仓库的直接落地建议

如果按最小改造量推进，建议下一步改成：

1. `vernflow-ai/app/modules/rag/`
   - `retrievers/`
   - `selectors/`
   - `generators/`
   - `profiles/`

2. 定义统一接口：
   - `retrieve(query, chunks, config) -> candidates`
   - `select(query, candidates, config) -> evidences`
   - `generate(query, evidences, config) -> answer`

3. 先落 4 个 profile：
   - `tfidf_topk`
   - `sparse_topk`
   - `dense_topk`
   - `hybrid_mmr`

4. assistant 和 workbench 都只引用 profile 名称，不再硬编码算法。

---

## 参考资料

- Lewis et al., 2020, *Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks*  
  [https://arxiv.org/abs/2005.11401](https://arxiv.org/abs/2005.11401)
- Karpukhin et al., 2020, *Dense Passage Retrieval for Open-Domain Question Answering*  
  [https://arxiv.org/abs/2004.04906](https://arxiv.org/abs/2004.04906)
- Carbonell and Goldstein, 1998, *The Use of MMR, Diversity-Based Reranking for Reordering Documents and Producing Summaries*  
  [https://www.cs.cmu.edu/~jgc/publication/The_Use_MMR_Diversity_Based_LTMIR_1998.pdf](https://www.cs.cmu.edu/~jgc/publication/The_Use_MMR_Diversity_Based_LTMIR_1998.pdf)
- Lin and Bilmes, 2011, *A Class of Submodular Functions for Document Summarization*  
  [https://aclanthology.org/P11-1052/](https://aclanthology.org/P11-1052/)
- Microsoft GraphRAG  
  [https://www.microsoft.com/en-us/research/project/graphrag/](https://www.microsoft.com/en-us/research/project/graphrag/)
- Microsoft GraphRAG GitHub  
  [https://github.com/microsoft/graphrag](https://github.com/microsoft/graphrag)
- LightRAG  
  [https://arxiv.org/abs/2410.05779](https://arxiv.org/abs/2410.05779)
- LightRAG GitHub  
  [https://github.com/HKUDS/LightRAG](https://github.com/HKUDS/LightRAG)
- HippoRAG  
  [https://arxiv.org/abs/2405.14831](https://arxiv.org/abs/2405.14831)
- Haystack retrievers  
  [https://docs.haystack.deepset.ai/v2.9/docs/retrievers](https://docs.haystack.deepset.ai/v2.9/docs/retrievers)
- LlamaIndex RetrieverQueryEngine / retriever API  
  [https://developers.llamaindex.ai/python/framework-api-reference/query_engine/retriever/](https://developers.llamaindex.ai/python/framework-api-reference/query_engine/retriever/)
- LangChain retrievers  
  [https://docs.langchain.com/oss/python/integrations/retrievers/index](https://docs.langchain.com/oss/python/integrations/retrievers/index)
- FlashRAG  
  [https://arxiv.org/abs/2405.13576](https://arxiv.org/abs/2405.13576)
- pgvector  
  [https://github.com/pgvector/pgvector](https://github.com/pgvector/pgvector)
