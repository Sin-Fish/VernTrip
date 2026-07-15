# VernFlow RAG 能力对照实验流程

## 1. 文档目的

本文档用于指导 VernFlow 项目的 RAG 模块能力对照实验。目标不是一次性做完所有论文式评测，而是在当前工程结构下，用一套可执行、可复现、可交接的流程，对不同 RAG profile 的效果进行横向比较。

本文档适用于以下场景：

- 为比赛材料补充实验结果
- 为后续选择默认 RAG profile 提供依据
- 将实验任务分配给学弟学妹或协作者执行
- 对 `retriever / selector / graph_strategy` 三层改动进行效果核对

## 2. 当前项目中的 RAG 对照对象

当前项目的 RAG 配置注册表位于：

- [registry.py](/D:/Aeolyn/MaraudersMap/Work/Computer/project/VernFlow/vernflow-ai/app/modules/rag/registry.py)

当前可用 profile 主要分为四类：

### 2.1 稀疏检索基线

- `tfidf_topk_basic`
- `tfidf_mmr_basic`
- `tfidf_coverage_basic`
- `tfidf_submodular_basic`
- `bm25_topk_basic`
- `bm25_mmr_basic`
- `bm25_coverage_basic`
- `bm25_submodular_basic`

### 2.2 稀疏混合基线

- `hybrid_rrf_basic`
- `hybrid_mmr`
- `hybrid_coverage`
- `hybrid_submodular`
- `hybrid_random_walk`
- `hybrid_submodular_random_walk`

### 2.3 向量检索基线

- `dense_pgvector_topk_basic`
- `dense_pgvector_mmr_basic`
- `dense_pgvector_coverage_basic`
- `dense_pgvector_submodular_basic`
- `dense_pgvector_submodular_random_walk`

### 2.4 向量混合检索

- `hybrid_pgvector_topk_basic`
- `hybrid_pgvector_mmr`
- `hybrid_pgvector_coverage`
- `hybrid_pgvector_submodular`
- `hybrid_pgvector_random_walk`
- `hybrid_pgvector_submodular_random_walk`

说明：

- `tfidf / bm25 / hybrid_rrf` 更适合做第一轮基线比较
- `mmr / coverage / submodular` 主要体现 `selector` 差异
- `random_walk` 主要体现 `graph_strategy` 差异
- `dense_pgvector_*` 和 `hybrid_pgvector_*` 依赖 PostgreSQL + pgvector 环境，不建议和 H2 模式混在同一轮里直接比较

## 3. 实验总原则

为保证结果可比，所有实验都必须满足以下条件：

1. 同一轮实验使用同一批文档。
2. 同一轮实验使用同一批问题。
3. 同一轮实验的工作区范围保持一致。
4. 同一轮实验的模型配置保持一致。
5. 除待比较 profile 外，不额外改动其他配置。
6. 每次切换 `RAG_PROFILE` 后，重新启动相关服务。

如果这些条件不能保证，则实验结果只能作为现象参考，不能用于正式对照结论。

## 4. 建议的实验轮次

不建议一开始把所有 profile 一次性全部比较。推荐分四轮进行。

### 4.1 第一轮：检索器基线比较

目的：先回答“哪一类检索路线更适合作为主干基线”。

建议比较的 profile：

- `tfidf_topk_basic`
- `bm25_topk_basic`
- `hybrid_rrf_basic`
- `dense_pgvector_topk_basic`
- `hybrid_pgvector_topk_basic`

说明：

- 如果当前环境尚未准备好 PostgreSQL + pgvector，则先只做前三个
- 第一轮不引入 `mmr / coverage / submodular / random_walk`
- 第一轮只看检索路线本身，不看高级选择器和重排器

### 4.2 第二轮：证据选择器消融实验

目的：回答“在同一种检索器下，哪种证据选择方式更合适”。

建议先在 `bm25` 或 `tfidf` 上做一组：

- `bm25_topk_basic`
- `bm25_mmr_basic`
- `bm25_coverage_basic`
- `bm25_submodular_basic`

如果第一轮发现 `tfidf` 更稳，也可以换成：

- `tfidf_topk_basic`
- `tfidf_mmr_basic`
- `tfidf_coverage_basic`
- `tfidf_submodular_basic`

说明：

- 这一轮只比较 `selector`
- `retriever` 固定不变
- 这一轮结束后，通常能判断 `TopK / MMR / Coverage / Submodular` 谁更适合本项目

### 4.3 第三轮：图策略消融实验

目的：回答“加入 random_walk 重排后是否真的有收益”。

建议成对比较：

- `hybrid_submodular` vs `hybrid_submodular_random_walk`
- `dense_pgvector_submodular_basic` vs `dense_pgvector_submodular_random_walk`
- `hybrid_pgvector_submodular` vs `hybrid_pgvector_submodular_random_walk`

说明：

- 这一轮只比较是否引入 `random_walk`
- 应尽量在前两轮表现较好的 retriever + selector 组合上进行
- 如果前两轮结果表明 `submodular` 并不稳定，这一轮可以缩小规模

### 4.4 第四轮：端到端 shortlist 比较

目的：从前 3 轮中挑出 3 到 5 个最佳 profile，做最终结果比较。

建议这一轮只保留少数候选，例如：

- 一个稀疏基线最优
- 一个混合基线最优
- 一个向量路线最优
- 一个带 `random_walk` 的最优组合

这一轮的结果最适合写入比赛材料。

## 5. 题集准备

实验质量很大程度上取决于题集，而不是只取决于模型。

### 5.1 推荐题量

第一版建议准备 `60` 题左右，后续再扩充到 `80-100` 题。

### 5.2 题型分布建议

推荐按以下结构准备：

- `20` 题：单文档事实问答
- `15` 题：多文档汇总
- `15` 题：对比与归纳
- `10` 题：范围约束与抗干扰问题

说明：

- “单文档事实问答”用于看基础命中能力
- “多文档汇总”用于看 evidence aggregation 能力
- “对比与归纳”用于看答案完整度和覆盖度
- “范围约束与抗干扰”用于看系统是否会跑出当前 scope

### 5.3 每题需要标注的内容

每个问题至少要有以下字段：

| 字段 | 含义 |
| --- | --- |
| `question_id` | 题目编号 |
| `question_type` | 题型 |
| `question` | 问题正文 |
| `scope_definition` | 使用哪些文件/目录作为范围 |
| `gold_answer_points` | 标准答案要点 |
| `gold_evidence` | 标准证据文件或 chunk |
| `notes` | 备注 |

建议采用表格或 CSV 保存，便于后续汇总。

### 5.4 标准答案的写法

标准答案不要写成长段话，而要拆成“要点列表”。

例如：

问题：`VernFlow 当前主要包含哪些系统模块？`

标准答案要点：

1. 用户前台
2. 管理员后台
3. Spring Boot 后端
4. FastAPI AI 子服务

这样后续更容易计算完整度和正确率。

## 6. 实验执行方式

### 6.1 推荐执行方式

第一版实验建议采用“半自动 + 人工标注”的方式。

推荐顺序：

1. 先固定文档范围和问题集
2. 切换 `RAG_PROFILE`
3. 用统一入口发起问题
4. 记录回答、证据、耗时和异常
5. 再做人为打分和汇总

### 6.2 建议使用的入口

端到端问答建议统一走：

- 产品前台的 assistant 页面
- 或后端 `/api/assistant/sessions/ask`

纯检索能力对照建议走：

- AI 服务 `/ai/rag-retrieve`
- 或系统中的 `local-retrieval` 节点对应能力

说明：

- 若只看最终回答效果，走 assistant 足够
- 若要细看 evidence 质量，建议额外保留 retrieve-only 结果

### 6.3 切换 profile 的建议流程

1. 修改根目录 `.env.local` 中的 `RAG_PROFILE`
2. 重启后端
3. 如 embedding 或模型配置有变化，再重启 AI 服务
4. 确认当前 profile 已生效后再开始记录结果

### 6.4 每轮实验的基本步骤

1. 确认当前 profile
2. 清空旧的记录表或新建一张实验结果表
3. 按题集顺序逐题执行
4. 每题记录回答文本、证据条目、耗时、异常情况
5. 本轮结束后统一打分
6. 汇总本轮均值、中位数和异常率

## 7. 指标设计

指标不建议只看“回答像不像”。至少要分成三层。

### 7.1 必做指标

以下指标建议作为第一版必做指标。

#### 1. 答案正确率

定义：最终答案是否答对核心问题。

建议打分：

- `0`：错误明显，或核心事实错误
- `1`：部分正确，但存在缺漏或混淆
- `2`：基本正确，核心事实无明显错误

#### 2. 答案完整度

定义：标准答案中的要点被覆盖了多少。

建议打分：

- `0`：只覆盖少量要点
- `1`：覆盖一半左右
- `2`：大部分或全部关键要点已覆盖

#### 3. 证据命中率

定义：返回的证据中是否包含标准证据。

可用最简单记法：

- `0`：未命中
- `1`：命中

如果后续精细化，可以补：

- `Evidence Precision`
- `Evidence Recall`
- `Evidence F1`

#### 4. 响应时间

定义：从发起请求到得到结果所耗费的时间。

建议记录：

- `latency_ms`

#### 5. 失败率

定义：本轮中报错、超时、空回答、空证据的比例。

建议记录：

- `success`
- `error_type`

### 7.2 推荐扩展指标

如果时间允许，可以继续补以下指标。

#### 1. Retrieval Hit@k

定义：Top-k 返回结果中是否包含标准证据。

适合做 retrieve-only 对照。

#### 2. MRR

定义：标准证据第一次出现的位置越靠前，分数越高。

适合比较 retriever。

#### 3. Scope Leakage Rate

定义：回答或证据是否跑出了当前工作区范围。

这是 VernFlow 很有项目特色的指标，建议至少在“范围约束题”中统计。

#### 4. Source Diversity

定义：最终证据是否过度集中在同一文件或同一 chunk 附近。

该指标对 `mmr / coverage / submodular` 比较有意义。

## 8. 结果记录模板

### 8.1 题集表

建议准备一张题集表：

| question_id | question_type | scope_definition | question | gold_answer_points | gold_evidence | notes |
| --- | --- | --- | --- | --- | --- | --- |

### 8.2 实验结果表

建议每个 profile 对每道题记录一行：

| profile_id | question_id | answer_text | evidence_list | correctness_score | completeness_score | evidence_hit | latency_ms | success | error_type | scorer | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

### 8.3 汇总表

建议每个 profile 最后汇总：

| profile_id | correctness_avg | completeness_avg | evidence_hit_rate | latency_p50 | latency_p95 | failure_rate | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- |

## 9. 分工建议

如果实验需要交给多人执行，建议至少拆成 3 个角色：

### 9.1 题集与标注负责人

职责：

- 准备题集
- 标标准答案要点
- 标标准证据

### 9.2 实验执行负责人

职责：

- 切换 profile
- 实际跑问题
- 记录回答、证据和耗时

### 9.3 结果复核负责人

职责：

- 统一打分口径
- 复核有争议的题目
- 汇总最终表格

## 10. 本轮建议的最小可执行版本

如果时间有限，建议先完成一个“最小可执行版本”：

### 10.1 题量

- 先做 `30` 题

### 10.2 首轮比较对象

- `tfidf_topk_basic`
- `bm25_topk_basic`
- `hybrid_rrf_basic`
- `hybrid_pgvector_topk_basic`

### 10.3 只记录 4 个核心指标

- `答案正确率`
- `答案完整度`
- `证据命中率`
- `响应时间`

### 10.4 第一版输出物

第一版至少交付：

1. 题集表
2. 每题实验结果表
3. profile 汇总表
4. 一页文字结论

这四项交齐，就已经能支撑内部对照和比赛材料初稿。

## 11. 结论使用建议

实验结果最终建议用于回答以下问题：

1. 当前项目默认 profile 是否需要调整。
2. `retriever` 层应该优先保留哪条主干路线。
3. `selector` 层是否值得默认启用 `mmr / coverage / submodular`。
4. `random_walk` 是否带来真实收益，还是只增加复杂度。
5. 哪一组 profile 最适合写进比赛材料。

实验报告中不建议只给一个“总分冠军”。更好的写法是分别给出：

- 检索效果最好的是谁
- 证据质量最好的是谁
- 最终回答最稳的是谁
- 速度最快的是谁

这样更符合 VernFlow 这种分层可插拔 RAG 系统的实际特点。
