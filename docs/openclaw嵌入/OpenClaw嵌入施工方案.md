# VernTrip OpenClaw 嵌入施工方案

> 文档状态：施工中  
> 基线日期：2026-07-16  
> OpenClaw 基线：`2026.7.1`  
> 实施原则：OpenClaw 负责智能体运行，VernTrip 负责业务、权限与数据，不直接修改 OpenClaw 核心源码。

## 1. 建设目标

把 OpenClaw 嵌入 VernTrip，使其成为系统内部的主要 Agent Runtime，而不是前端直接调用的外部聊天 API。

完成后的职责划分如下：

- OpenClaw 负责模型调用、Agent Loop、Skill 选择、工具调用、会话上下文和运行事件。
- VernTrip Spring 后端继续负责登录鉴权、用户隔离、工作区权限、业务数据、会话展示和审计记录。
- VernTrip OpenClaw 插件负责把现有 RAG、工作区和工作台能力注册成 OpenClaw 原生工具。
- `vernflow-ai` 暂时保留为检索、解析和旧链路回退服务，后续再按业务改造结果决定是否拆分。
- 浏览器只访问 Spring 后端，不持有 OpenClaw Gateway Token，也不直接访问 Gateway。

本次嵌入不等于把 OpenClaw 源码复制进业务代码后大规模魔改。采用“固定版本的 OpenClaw 发行版 + VernTrip 原生插件 + VernTrip Skills + 内部网关”的方式，可独立升级、测试和回退。

## 2. 当前系统基线

当前助手请求链路：

```text
vernflow-web
  -> POST /api/assistant/sessions/ask
  -> AssistantController
  -> WorkspaceAssistantService
  -> RagProfileFacade / vernflow-ai
  -> assistant_session.messages_json
```

当前工程中已经存在可复用能力：

- 用户登录、会话 Cookie 和用户数据隔离。
- 助手会话、工作文件范围和消息持久化。
- 工作区文件读取、RAG Profile 和引用证据。
- 工作台工具定义，包括范围读取、本地检索、联网补充、资料整理、产物写入和断言检查。
- PostgreSQL/pgvector、Spring Boot、FastAPI 和 Vue 的基础部署结构。

因此不重写现有项目，而是在助手服务与模型/工具执行之间加入 OpenClaw Runtime。

## 3. 目标架构

```text
用户浏览器
   |
   | VernTrip 登录态
   v
Spring Boot 业务后端
   |  - 校验用户、工作区、会话
   |  - 生成不透明 session key
   |  - 保存展示层消息与运行摘要
   |
   | 内网 + Gateway Token
   v
OpenClaw Gateway / Agent Runtime
   |  - Agent Loop / 模型 / Skills / Memory
   |  - 选择并执行 VernTrip Plugin Tools
   |
   | 服务凭证 + 不透明 session key
   v
Spring Boot 内部工具接口
   |  - 按服务端映射恢复用户身份
   |  - 执行权限检查和业务操作
   v
Workspace / RAG / Workbench / Tourism Domain
```

关键边界：

1. OpenClaw 是智能体主引擎，但不是 VernTrip 的用户中心或业务数据库。
2. Spring 后端是唯一公网业务入口，也是多用户权限的最终裁决者。
3. 插件工具参数中不允许模型直接传入 `userId`、文件绝对路径或服务密钥。
4. OpenClaw 只通过内部接口访问 VernTrip 数据，所有读写都经过后端权限校验。
5. 旧助手保留为 Feature Flag 回退路径，完成验收前不删除。

## 4. 代码与部署目录

```text
VernTrip/
├─ openclaw-runtime/
│  ├─ README.md
│  ├─ config/
│  │  └─ openclaw.example.json5
│  ├─ plugin/
│  │  ├─ package.json
│  │  ├─ openclaw.plugin.json
│  │  ├─ tsconfig.json
│  │  └─ src/
│  │     ├─ index.ts
│  │     └─ config.ts
│  └─ skills/
│     └─ verntrip-foundation/
│        └─ SKILL.md
├─ deploy/
│  └─ docker-compose.openclaw.yml
├─ vernflow-backend/
│  └─ OpenClaw Client、内部工具接口、会话映射
└─ docs/openclaw嵌入/
   └─ OpenClaw嵌入施工方案.md
```

插件作为 VernTrip 自有包维护，使用 `openclaw/plugin-sdk/*` 的稳定子路径，不依赖 OpenClaw 内部未公开模块。

## 5. 分阶段施工

### 阶段 0：基线与方案

施工内容：

- 核对当前助手、RAG、工作台、权限和部署结构。
- 固定 OpenClaw 兼容版本与 Node 版本要求。
- 明确运行时边界、身份传递方式和回退策略。

验收标准：

- 本文档进入仓库。
- 不修改现有助手行为。

### 阶段 1：OpenClaw 运行时与插件骨架

施工内容：

- 创建 `@verntrip/openclaw-plugin` TypeScript ESM 插件。
- 创建合法的 `openclaw.plugin.json`，声明工具所有权和配置 Schema。
- 注册只读的 `verntrip_runtime_status` 工具，验证插件可发现、可加载、可执行。
- 创建基础 Skill 和 OpenClaw 示例配置。
- 创建独立 Compose 叠加文件，不污染现有基础 Compose。

验收标准：

- JSON/JSON5 和 TypeScript 配置通过静态检查。
- 在符合 Node `22.22.3+` 的环境中可构建插件。
- OpenClaw 执行 `plugins inspect verntrip --runtime --json` 能看到插件和工具。
- 现有 VernTrip 服务无需 OpenClaw 也能正常启动。

### 阶段 2：只读业务工具闭环

施工内容：

- 后端增加 OpenClaw 内部服务鉴权和会话上下文映射。
- 增加第一个只读内部接口，建议从 `workspace_scope_read` 开始。
- 插件注册 `verntrip_workspace_scope_read`，只接收业务必要参数，不接收用户身份。
- 使用 OpenClaw 受信任运行上下文中的 session key 调用后端。
- 为越权、无效会话、超时和后端不可用增加测试。

验收标准：

- 用户只能读取自己会话绑定的工作区范围。
- Prompt 注入无法改变用户身份或绕过文件权限。
- 工具返回结构化证据和可展示引用，不返回服务器绝对路径。

### 阶段 3：助手主链路切换

施工内容：

- 增加 `ASSISTANT_ENGINE=legacy|openclaw|shadow`。
- Spring 后端通过 OpenClaw 的 `/v1/responses` 发起 Agent Run。
- 映射 VernTrip 会话与 OpenClaw session key。
- 将 OpenClaw 输出、工具事件和错误转换成现有助手响应模型。
- `shadow` 模式仅记录 OpenClaw 结果，不影响用户当前答案。

验收标准：

- `legacy` 模式行为与改造前一致。
- `openclaw` 模式支持连续多轮会话和引用展示。
- OpenClaw 超时或不可用时按配置回退旧 RAG，而不是丢失用户消息。
- Gateway Token 不出现在浏览器请求、前端构建产物或日志正文中。

### 阶段 4：工作台能力迁移

施工内容：

- 按风险从低到高迁移现有工具：范围读取、本地检索、联网补充、资料整理、断言检查、产物写入。
- 读取工具默认启用；联网和写入工具设为可选工具并配置 allowlist。
- 写入类操作加入预览、确认、幂等键和审计记录。
- 将 OpenClaw Agent 事件同步到工作台运行历史。

验收标准：

- 工具输入输出有固定 Schema 和自动化测试。
- 写操作不能越过会话工作区，重复调用不会产生重复产物。
- 工作台可展示工具选择、执行状态、证据和失败原因。

### 阶段 5：题目 6 文旅业务 Skills

施工内容：

- `tourism-consult`：景点、住宿、餐饮和文化知识咨询。
- `itinerary-plan`：根据人群、预算、日期、偏好和交通约束规划行程。
- `tourism-marketing`：生成平台适配的文旅营销内容。
- `cultural-product-design`：文创产品与服务创新方案。
- `customer-demand-match`：游客需求、资源和产品匹配。
- `tourism-fact-check`：时间、价格、开放状态、来源和宣传表述检查。

验收标准：

- 覆盖题目要求的景区、住宿、餐饮、营销、产品研发五类场景。
- 规划结果包含约束、依据、备选方案和风险提示。
- 时效性事实必须经过可追溯工具验证，不能只依赖模型记忆。
- 每个 Skill 都有示例任务、反例和最小评测集。

### 阶段 6：安全、评测与演示交付

施工内容：

- 完成越权、Prompt 注入、SSRF、密钥泄漏、工具滥用和资源限制测试。
- 建立文旅问答、行程规划、营销生成和需求匹配评测集。
- 准备无网络或模型异常时的演示降级方案。
- 固定镜像、插件包和配置版本，形成一键启动脚本。

验收标准：

- 核心演示流程可重复执行。
- 关键请求有 trace、运行耗时、工具记录和错误分类。
- 关闭 OpenClaw 叠加服务后可恢复旧助手链路。

## 6. 接口约定

### 6.1 VernTrip 调用 OpenClaw

计划使用 Gateway 的 OpenResponses 接口：

```http
POST /v1/responses
Authorization: Bearer <gateway-token>
X-OpenClaw-Agent-Id: verntrip-main
X-OpenClaw-Session-Key: verntrip:<opaque-session-id>
Content-Type: application/json
```

约束：

- Gateway Token 仅保存在后端环境变量或 Secret 中。
- session key 使用随机、不透明标识，不编码 `userId`、手机号等信息。
- Spring 后端维护 `VernTrip session -> OpenClaw session key -> user/workspace` 映射。
- Gateway 只允许内网访问；本地调试端口仅绑定 `127.0.0.1`。

### 6.2 OpenClaw 插件调用 VernTrip

内部接口统一放在：

```text
/api/internal/openclaw/v1/**
```

每次请求至少携带：

- 服务端凭证，证明调用方是 VernTrip OpenClaw 插件。
- OpenClaw session key，用于服务端恢复用户和工作区上下文。
- 请求幂等键，写操作必须提供。
- trace id，用于串联 Gateway、插件和后端日志。

模型可见参数只包含业务意图，例如查询文本、筛选条件和输出格式。身份、权限、密钥、服务器路径均由受信任运行时注入。

OpenClaw 工具策略使用 `profile=full` 作为候选基线，再由 `tools.allow` 精确收窄到两个 `verntrip_*` 只读工具；`minimal` 只包含 `session_status`，不能用于恢复插件工具。

## 7. 配置约定

计划新增环境变量：

```dotenv
ASSISTANT_ENGINE=legacy
OPENCLAW_BASE_URL=http://openclaw-gateway:18789
OPENCLAW_GATEWAY_TOKEN=
OPENCLAW_AGENT_ID=verntrip-main
OPENCLAW_TIMEOUT=180s
VERNTRIP_OPENCLAW_SERVICE_TOKEN=
OPENCLAW_SESSION_CONTEXT_TTL=30m
OPENCLAW_SHADOW_SAMPLE_RATE=1.0
OPENCLAW_SHADOW_TIMEOUT=60s
OPENCLAW_SHADOW_MAX_OUTPUT_TOKENS=1200
OPENCLAW_SHADOW_MAX_STORED_CHARS=12000
```

配置规则：

- 示例文件只保留空值或占位符，不提交真实 Token。
- 本地 OpenClaw 使用 `verntrip-deepseek/deepseek-chat` 自定义 Provider；`apiKey` 以结构化 SecretRef 引用 `DEEPSEEK_API_KEY`，不会写入 `openclaw.json`。
- OpenClaw 版本默认固定为 `2026.7.1`，升级必须先通过插件构建和回归测试。
- `legacy` 是接入期间默认值；完成阶段 3 验收后再改为 `openclaw`。
- 生产环境不使用 `latest` 镜像标签。

Windows 无 Docker 本地运行：

```powershell
# 首次安装固定版本 Node、OpenClaw 和 VernTrip 托管插件
.\scripts\openclaw\install.ps1

# 同时启动 Spring 后端与 OpenClaw Gateway，默认使用 openclaw
.\scripts\openclaw\start-stack.ps1

# 临时关闭影子流量，只运行旧助手
.\scripts\openclaw\start-stack.ps1 -AssistantEngine legacy

# 查看两个进程状态
.\scripts\openclaw\backend-status.ps1
.\scripts\openclaw\status.ps1

# 安全停止两个进程
.\scripts\openclaw\stop-stack.ps1
```

`start-backend.ps1` 会从已忽略的 `openclaw-runtime/state/runtime-secrets.json` 读取 Gateway Token 和服务令牌并注入 Spring 子进程，不需要把真实令牌提交到 `.env`。`start-stack.ps1` 默认使用 `openclaw`，仍可通过 `-AssistantEngine shadow` 进行旁路观察，或通过 `-AssistantEngine legacy` 无迁移回退。

Shadow 执行规则：

- 旧助手在原事务内生成并保存答案，Controller 收到成功结果后才将 OpenClaw 请求放入独立线程池。
- OpenClaw 超时、鉴权失败、队列满或结果解析失败都不会回滚旧答案，也不会改变 HTTP 响应。
- `OPENCLAW_SHADOW_SAMPLE_RATE` 控制进入影子链路的比例，取值范围为 `0.0` 到 `1.0`。
- V16 新增 `openclaw_shadow_run`，记录消息关联、运行状态、截断后的影子答案、Token 数、耗时和固定错误码；不重复保存用户问题。
- 日志只记录 run id、助手 session id 和错误码，不输出问题、答案、Token 或异常响应正文。

## 8. 安全红线

- 不允许浏览器直连 Gateway。
- 不允许把 OpenResponses 的共享 Token 当作普通低权限 API Token；它具备高权限，只能在可信内网使用。
- 不允许模型通过工具参数声明自己是谁。
- 不允许插件直接访问 VernTrip 数据库。
- 不允许初期给 Agent 整个项目目录的任意读写权限。
- 不允许默认开启写文件、Shell、浏览器自动化等高风险工具。
- 外部网页和用户文件一律视为不可信内容，不能覆盖系统指令或权限策略。
- 日志不得输出 Token、Cookie、完整用户文件正文和模型供应商密钥。

## 9. 回退策略

1. 助手引擎由 Feature Flag 控制，不删除 `WorkspaceAssistantService` 的旧 RAG 路径。
2. OpenClaw 配置、插件和 Skills 位于独立目录，可整体停用。
3. Compose 使用叠加文件；不加载叠加文件时，原有三个后端服务保持原样。
4. 数据库变更只新增映射和运行记录，不复用或破坏旧消息字段。
5. 阶段 3 先运行 `shadow`，对比正确率、延迟和失败率后再切主。

## 10. 测试矩阵

| 层级 | 最低测试内容 |
| --- | --- |
| 插件 | manifest 校验、TypeScript 构建、工具注册、配置默认值 |
| 后端 | 服务鉴权、session 映射、用户隔离、超时、回退、幂等 |
| 集成 | OpenResponses 请求、连续会话、工具调用、引用转换、错误映射 |
| 安全 | 越权、Prompt 注入、SSRF、路径穿越、Token 泄漏、工具 allowlist |
| 业务 | 文旅咨询、行程规划、营销内容、文创方案、需求匹配、事实检查 |
| 演示 | 冷启动、正常链路、降级链路、历史恢复、运行轨迹展示 |

## 11. 当前施工记录

- [x] 完成现有助手、RAG、工作台和部署边界盘点。
- [x] 确认采用外部原生插件，不 fork OpenClaw 核心。
- [x] 固定首个兼容基线为 OpenClaw `2026.7.1`。
- [x] 完成插件、基础 Skill、自定义镜像和 Compose 叠加骨架。
- [x] 完成插件 TypeScript 检查、构建和 npm 打包清单检查。
- [x] 使用隔离的 Node `24.15.0` 完成 `npm-pack:` 安装与 `plugins inspect --runtime`。
- [x] 完成后端只读工具接口。
- [x] 完成 Windows 无 Docker 安装、启停和状态脚本。
- [x] 完成 OpenClaw session key 到用户工作区范围的服务端映射。
- [x] 完成 `verntrip_workspace_scope_read` 插件工具与真实链路冒烟测试。
- [x] 完成 `shadow` 模式。
- [x] 切换游客端对话主链路，并保留本地规则降级。
- [ ] 完成题目 6 文旅 Skills 和评测。

本阶段验收结果（2026-07-16）：

- `npm run check` 通过。
- `npm run build` 通过。
- `npm pack` 通过，运行依赖 `typebox` 已打入插件包。
- OpenClaw `plugins install npm-pack:` 通过，并正确建立宿主 `openclaw` peer link。
- `plugins inspect verntrip --runtime --json` 返回 `status: loaded`、`activated: true`，识别到 `verntrip_runtime_status` 和 `verntrip_workspace_scope_read`。
- `verntrip_runtime_status` 最小运行测试返回 `status: ready`。
- Spring 主 Flyway 已执行 V15，创建 `openclaw_session_context`；主数据源与 RAG 数据源已恢复为显式边界。
- `OpenClawInternalToolIntegrationTest` 通过，覆盖服务令牌、错误令牌、会话范围恢复和响应截断。
- 插件 Node 测试通过，确认请求体不包含 `userId`，session key 只从受信任工具上下文进入请求头。
- 真实冒烟链路 `插件 -> Spring -> 用户工作区` 通过，返回绑定文档 `assistant-check.md`。
- Windows 原生 Gateway 和 Spring 后端均通过健康检查，一键启动脚本通过幂等验证。
- 后端全套回归 42 项中 41 项通过；既有 `WorkbenchControllerIntegrationTest.shouldReorganizeManagedOutputsWhenWholeWorkspaceOnlyContainsOrganizerFiles` 在全局范围存在其他文件时仍会因固定目标冲突失败，与 OpenClaw 链路无关，留待工作台阶段修复。
- JSON、Compose YAML 和 Git 空白检查通过。

Shadow 阶段验收结果（2026-07-17）：

- 完成 `/api/assistant/sessions/ask` 成功响应后的异步影子派发，`legacy` 响应模型和持久化结构保持不变。
- 完成 OpenResponses 非流式客户端，Gateway Token 和不透明 session key 只通过服务端请求头传递。
- 完成 V16 `openclaw_shadow_run` 审计表，支持成功、失败、延迟和 Token 用量评估。
- `OpenClawGatewayClientTest` 通过，覆盖可信请求头、请求体和 Responses 输出解析。
- `OpenClawShadowIntegrationTest` 通过，确认 Gateway 被阻塞时旧助手仍先返回，影子记录随后独立完成。
- `AssistantControllerIntegrationTest` 回归通过，`legacy` 默认模式不产生影子副作用。
- Windows Gateway 已使用 `verntrip-deepseek/deepseek-chat` 启动，API Key 仅通过 `DEEPSEEK_API_KEY` SecretRef 解析；OpenResponses 模型预检成功。
- 真实助手请求主响应耗时 58ms；同一请求的 Shadow 运行在 3245ms 后成功，V16 记录包含可信 session key、Token 用量和空错误码，未改变用户收到的旧助手答案。
- 最终状态检查显示 Spring 后端与 OpenClaw Gateway 均健康，后端明确运行在 `Assistant engine: shadow`。

游客端主链路验收结果（2026-08-04）：

- `start-stack.ps1` 默认引擎调整为 `openclaw`，并保留 `shadow`、`legacy` 两种回退启动方式。
- 新增 `verntrip_tourism_catalog_read`，通过服务令牌和受信任会话读取后台文旅资源，不接受 `userId`、Cookie 或内部令牌参数。
- 修正 OpenClaw 规范化会话键与 VernTrip 原始会话键之间的映射，覆盖 `agent:<agent>:verntrip:<uuid>` 形式。
- 插件 Node 测试和 `OpenClawInternalToolIntegrationTest` 通过，覆盖文旅目录读取、错误鉴权和身份参数隔离。
- `plugins inspect verntrip --runtime --json` 返回三个已加载工具：运行诊断、工作区读取和文旅目录读取。
- 真实游客请求返回 `engine=openclaw`，Agent 成功读取杭州资源库并引用其中的非遗、餐饮和亲子资源；Gateway 或模型异常时仍返回 `local-fallback`。
- 前端生产构建、后端可执行包构建和主链路运行检查通过，前端、Spring 后端与 OpenClaw Gateway 均可用。

已知本机环境限制：系统 Node 为 `22.20.0`，低于 OpenClaw 要求的 `22.22.3`，且当前未安装 Docker。项目已内置隔离的 Node `24.15.0` 并完成原生 Gateway 验收，因此日常开发不需要 Docker；自定义镜像构建与 Compose 部署仍需在安装 Docker Desktop 后单独验证。

## 12. 官方依据

- [OpenClaw Building plugins](https://docs.openclaw.ai/plugins/building-plugins)
- [OpenClaw Plugin SDK overview](https://docs.openclaw.ai/plugins/sdk-overview)
- [OpenClaw Plugin manifest](https://docs.openclaw.ai/plugins/manifest)
- [OpenClaw Docker](https://docs.openclaw.ai/install/docker)
- [OpenClaw OpenResponses API](https://docs.openclaw.ai/gateway/openresponses-http-api)
