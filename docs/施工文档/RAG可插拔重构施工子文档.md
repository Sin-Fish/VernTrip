# VernFlow RAG 可插拔重构施工子文档

## 1. 文档定位

本文档是 VernFlow 主施工文档在 `RAG / 检索 / 证据选择 / 图策略` 方向上的子施工文档，用于指导后续一段时间的代码重构、能力接入、实验对照与验收。

本文档默认继承以下上位文档目标：

- [文脉VernFlow_项目施工文档_v2.0_细化版.docx](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/docs/V0文档/文脉VernFlow_项目施工文档_v2.0_细化版.docx>)
- [文脉VernFlow_比赛作品报告.docx](</D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/docs/V0文档/文脉VernFlow_比赛作品报告.docx>)
- [RAG可插拔调研报告.md](/D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/docs/RAG可插拔调研报告.md)

## 2. 目标与边界

### 2.1 总目标

将当前项目中的轻量固定式 RAG，重构为一套：

- 对外表现为单一 `RAG Profile`
- 对内可按层替换
- 可支撑 assistant 与 workbench 共享
- 可支撑后续横向实验对照
- 可支撑随机游走与子模覆盖等创新策略接入

的可插拔 RAG 内核。

### 2.2 本次重构的核心目标

1. 不再把 RAG 写死为单一 `TF-IDF + TopK + 生成`
2. 建立清晰的检索层、选择层、图策略层边界
3. 让 assistant 与 workbench 共用同一套 RAG 内核
4. 让每次会话、每次任务运行都可记录所使用的 RAG 配置
5. 让后续实验可以在“不改业务主流程”的前提下切换方案

### 2.3 本次不做的事

1. 不在这一阶段追求最强效果
2. 不一次性接入所有开源 RAG 框架
3. 不把 GraphRAG / LightRAG 整套直接塞进主链路
4. 不在本次先做完整评测平台
5. 不在本次先实现全部创新算法

## 3. 当前问题

当前仓库中的 RAG 仍属于过渡版实现，主要问题如下：

1. 每次检索都会现读文件、现切 chunk、现做检索，缺少持久化索引
2. assistant 与 workbench 虽然已经共享一部分能力，但共享边界仍不清晰
3. 检索、证据选择、图策略没有清晰分层
4. 无法方便地对比 `TF-IDF / Sparse / Dense / Hybrid / MMR / Coverage`
5. 无法自然接入后续的随机游走与子模覆盖算法

## 4. 架构原则

### 4.1 对外单模块，对内分层

系统对外应表现为一个完整 `RAG Profile`，而不是要求业务层显式拼接多个组件。

系统对内必须拆成独立层，以便替换、实验和记录：

1. `DocumentParser`
2. `Chunker`
3. `Retriever`
4. `Selector`
5. `GraphStrategy`
6. `Generator`

### 4.2 assistant 与 workbench 共享内核

- assistant 负责 `query`
- workbench 负责 `retrieve / select / inspect / write` 的任务编排

但两者都必须共享同一套 RAG 内核，不允许未来再长出两套平行系统。

### 4.3 创新层与基线层分离

按照现有文档定义：

- `Retriever` 主要承担基线层和可插拔层职责
- `Selector` 是第一创新核心层
- `GraphStrategy` 是第二创新核心层

因此：

- 检索层优先保证可替换与可对比
- 选择层优先承接 `MMR / Coverage / Submodular`
- 图层优先承接 `Random Walk / Graph Prior`

## 5. 目标架构

### 5.1 Profile 结构

后续统一使用如下抽象：

```text
RagProfile
= DocumentParser
+ Chunker
+ Retriever
+ Selector
+ GraphStrategy
+ Generator
```

说明：

- `DocumentParser` 与 `Chunker` 是基础层
- `Retriever` 负责召回候选证据
- `Selector` 负责从候选中筛出最终证据集合
- `GraphStrategy` 负责关系扩展、随机游走、图先验重排
- `Generator` 负责将证据交给大模型生成回答或结果

### 5.2 推荐目录结构

建议将 `vernflow-ai/app/modules/rag/` 重构为：

```text
rag/
  __init__.py
  contracts/
    parser.py
    chunker.py
    retriever.py
    selector.py
    graph_strategy.py
    generator.py
    profile.py
  parsers/
    workspace_parser.py
  chunkers/
    basic_chunker.py
  retrievers/
    tfidf_retriever.py
    sparse_fts_retriever.py
    dense_pgvector_retriever.py
    hybrid_retriever.py
  selectors/
    topk_selector.py
    mmr_selector.py
    coverage_selector.py
    submodular_selector.py
  graph_strategies/
    noop_graph_strategy.py
    random_walk_rerank.py
  generators/
    deepseek_generator.py
  profiles/
    tfidf_topk_basic.py
    sparse_topk_basic.py
    dense_topk_basic.py
    hybrid_mmr.py
    hybrid_coverage.py
```

### 5.3 后端适配结构

建议在 `vernflow-backend` 中增加统一的 RAG profile 调用入口，而不是让 assistant 和 workbench 直接拼请求体：

```text
modules/
  rag/
    service/
      RagProfileFacade.java
      RagExperimentResolver.java
    dto/
      RagProfileRequest.java
      RagProfileResponse.java
```

## 6. 第一阶段落地策略

### 6.1 第一阶段目标

先把“固定实现”改造成“可插拔骨架”，而不是一开始就追求算法数量。

第一阶段只要求：

1. 当前 TF-IDF 实现被正式收编为 baseline profile
2. assistant 与 workbench 改成走 profile 调用
3. profile 支持配置式切换
4. 运行记录能记住使用的是哪个 profile

### 6.2 第一阶段默认 profile

第一阶段唯一必须稳定可用的 profile：

- `tfidf_topk_basic`

含义：

- retriever: `tfidf`
- selector: `topk`
- graphStrategy: `none`
- generator: `deepseek`

该 profile 的职责：

- 作为系统重构期间的稳定保底方案
- 作为后续所有实验的最低基线

## 7. 第二阶段落地策略

### 7.1 检索层扩展

第二阶段优先补三种 retriever：

1. `sparse_fts`
2. `dense_pgvector`
3. `hybrid_sparse_dense`

推荐优先级：

1. `sparse_fts`
2. `dense_pgvector`
3. `hybrid_sparse_dense`

原因：

- `sparse_fts` 接入成本低，适合先替换 TF-IDF 的工程基线
- 当前环境已具备 PostgreSQL + pgvector，`dense_pgvector` 可直接落地
- `hybrid` 是后续最可能成为默认主链的产品候选

### 7.2 第二阶段产出

至少形成以下 profile：

1. `tfidf_topk_basic`
2. `sparse_topk_basic`
3. `dense_topk_basic`
4. `hybrid_topk_basic`

## 8. 第三阶段落地策略

### 8.1 选择层扩展

第三阶段重点做选择层，这一层是后续主要创新层。

优先顺序：

1. `topk_selector`
2. `mmr_selector`
3. `coverage_selector`
4. `submodular_selector`

### 8.2 选择层定位

- `TopK`：最低基线
- `MMR`：去冗余 baseline
- `Coverage`：任务导向证据覆盖
- `Submodular Coverage`：更正式的全局覆盖优化

### 8.3 第三阶段产出

至少形成以下 profile：

1. `hybrid_topk_basic`
2. `hybrid_mmr`
3. `hybrid_coverage`
4. `hybrid_submodular`

## 9. 第四阶段落地策略

### 9.1 图策略层扩展

第四阶段接入图策略层，重点不是“重新做一个检索器”，而是在候选证据之上做关系先验重排和扩展。

建议优先顺序：

1. `none`
2. `random_walk_rerank`
3. `graph_prior_rerank`

### 9.2 图策略层定位

图策略层负责：

- 多跳扩展
- 关系邻接扩展
- 随机游走重排
- 结构先验增强

它不负责直接替代 retriever。

### 9.3 第四阶段产出

至少形成以下 profile：

1. `hybrid_coverage`
2. `hybrid_random_walk`
3. `hybrid_graph_prior`

## 10. assistant 与 workbench 接入方式

### 10.1 assistant

assistant 不应再直接依赖某个固定 RAG 实现，而应统一调用：

- `profile.query()`

assistant 关心：

- 最终回答
- 引用证据
- 当前 profile 标识

### 10.2 workbench

workbench 不应自己实现另一套检索系统，而应按节点调用 profile 的不同能力：

- `scope-read`：读范围
- `local-retrieval`：`profile.retrieve()`
- `selector`：`profile.select()`
- `writer`：使用已选证据做结果写入
- `audit`：对输出做检查

### 10.3 统一记录

会话与运行记录都要保存：

- `profileId`
- `retrieverId`
- `selectorId`
- `graphStrategyId`
- `generatorId`
- 关键参数快照

## 11. 实验设计建议

### 11.1 对外实验单位

对外应以完整 `profile` 为实验单位，而不是让演示端直接暴露底层零件。

推荐实验组：

1. `tfidf_topk_basic`
2. `sparse_topk_basic`
3. `dense_topk_basic`
4. `hybrid_topk_basic`
5. `hybrid_mmr`
6. `hybrid_coverage`
7. `hybrid_submodular`
8. `hybrid_random_walk`

### 11.2 对内消融单位

内部分析时，可固定其余层，仅替换一层：

- 固定 retriever，只换 selector
- 固定 selector，只换 graphStrategy
- 固定 graphStrategy，只换 retriever

## 12. 兼容性要求

### 12.1 对随机游走的兼容性要求

后续若实现随机游走，当前架构必须满足：

1. 检索层输出的不只是纯文本，还要保留可追踪的 chunk 标识
2. selector 层输出的证据集合要能回溯到候选图节点
3. graphStrategy 层能读到 chunk 间的引用、目录、文档归属、任务归属信息

### 12.2 对子模覆盖的兼容性要求

后续若实现子模覆盖，当前架构必须满足：

1. retriever 输出候选集合分数
2. selector 可访问候选集合的文本、来源、任务槽位信息
3. 系统可以单独记录“候选集合”和“最终选择集合”

## 13. 任务拆解

### 13.1 P0：骨架重构

1. 抽 RAG contracts
2. 抽 `RagProfile`
3. 把当前实现收编为 `tfidf_topk_basic`
4. assistant 改为走 profile
5. workbench 改为走 profile

### 13.2 P1：检索层扩展

1. 接 `sparse_fts`
2. 接 `dense_pgvector`
3. 接 `hybrid_sparse_dense`
4. 增加 profile 配置切换

### 13.3 P2：选择层扩展

1. 接 `mmr_selector`
2. 接 `coverage_selector`
3. 接 `submodular_selector`
4. 将选择结果用于 assistant 与 workbench

### 13.4 P3：图层扩展

1. 抽 `graph_strategy`
2. 接 `random_walk_rerank`
3. 接 `graph_prior_rerank`

### 13.5 P4：实验与记录

1. 会话与任务记录 profile 元数据
2. 管理端查询 profile 使用情况
3. 输出实验对照结果

## 14. 验收标准

### 14.1 架构验收

1. `assistant` 与 `workbench` 不再直接依赖固定 RAG 实现
2. RAG 至少已按 profile 方式调用
3. baseline profile 可稳定运行

### 14.2 可插拔验收

1. 能在不改业务主流程代码的前提下切换 profile
2. 每次任务与会话都能记录当前 profile
3. 新增 retriever 或 selector 时，不需要改 assistant/workbench 主逻辑

### 14.3 实验验收

1. 至少可稳定跑通 3 组 profile 对照
2. 至少可稳定做一次 selector 层对照
3. 至少可复现实验所用 profile 配置

## 15. 当前建议

当前最优先事项不是继续补 workbench 单个节点，而是：

1. 先完成 `RagProfile` 骨架
2. 先收编当前 `TF-IDF + TopK + DeepSeek`
3. 先打通 assistant/workbench 的统一 profile 调用

在此之前，不建议继续堆更多节点级工具逻辑，否则后续返工成本会变高。

