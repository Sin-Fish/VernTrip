# VernFlow

VernFlow 是一个面向资料工作区的可审计智能体系统。当前仓库包含 4 个主要部分：

- `vernflow-web`：用户前台
- `vernflow-admin`：管理员后台
- `vernflow-backend`：Spring Boot 后端
- `vernflow-ai`：FastAPI AI 子服务

此外，游客对话助手支持 OpenClaw 引擎（可选，见 [1.7 完整模式](#17-完整模式openclaw-助手引擎可选)）：

- `openclaw-runtime`：VernTrip OpenClaw 插件、Skills 与本地运行环境
- `scripts/openclaw`：OpenClaw 安装、启动、状态与停止脚本

建议先按本文的"快速启动"流程完成本地联调，再根据需要查看 PostgreSQL、Docker 和正式部署部分。

## 1. 快速启动

本节给出最短启动流程。

### 1.1 先安装这些软件

- `Git`
- `JDK 21`
- `Node.js 20+`
- `npm`
- `Python 3.13` 或兼容版本

### 1.2 克隆仓库

```powershell
git clone <repository-url>
cd VernFlow
```

### 1.3 准备配置文件

项目现在统一使用**仓库根目录**的环境变量文件。

第一次启动前，先执行：

```powershell
Copy-Item .env.example .env.local
```

说明：

- `.env.example`：模板文件
- `.env.local`：你本机实际使用的配置
- `.env`：如果存在，一般作为共享默认值

本地直接启动项目时：

- `vernflow-web` 会读取根目录环境变量
- `vernflow-admin` 会读取根目录环境变量
- `vernflow-backend` 会读取根目录环境变量
- `vernflow-ai` 会读取根目录环境变量

**平时改配置，优先改根目录 `.env.local`。**

### 1.4 启动方式一：使用仓库脚本（推荐）

仓库提供三个开发启动脚本，分别在新终端中运行：

```powershell
# 终端 1：AI 子服务（强制 hash 轻量 embedding，跳过模型下载）
.\scripts\start-vernflow-ai-dev.cmd

# 终端 2：后端（使用独立 Gradle 依赖缓存 .gradle-home-rag）
.\scripts\start-vernflow-backend-dev.cmd

# 终端 3：用户前台（显式绑定 http://127.0.0.1:5173）
.\scripts\start-vernflow-web-dev.cmd
```

说明：

- AI 脚本调用系统 `python`，要求已按 [1.5.1](#151-ai-子服务) 安装依赖；如需真实 embedding 模型，改用手动方式启动 AI
- 管理后台暂无对应脚本，按 [1.5.4](#154-管理员后台) 手动启动
- 该方式不启动 OpenClaw 助手引擎，游客对话助手运行在内置 legacy 引擎上；需要完整助手功能见 [1.7](#17-完整模式openclaw-助手引擎可选)

### 1.5 启动方式二：手动启动

#### 1.5.1 AI 子服务

```powershell
cd vernflow-ai
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

启动后可访问：

- [http://localhost:8000/ai/health](http://localhost:8000/ai/health)

#### 1.5.2 后端

新开一个终端，回到仓库根目录：

```powershell
cd VernFlow
$env:SPRING_PROFILES_ACTIVE = "h2"
$env:GRADLE_USER_HOME = "$PWD\\.gradle-home"
.\gradlew :vernflow-backend:bootRun --no-daemon --console=plain
```

说明：

- 该模式适合本地快速联调
- `h2` 模式不需要你先安装 PostgreSQL
- 第一次运行会下载 Gradle 和 Maven 依赖，时间可能较长
- 如根目录 `.env` 已配置 `DB_URL`（PostgreSQL），则无需设置 `SPRING_PROFILES_ACTIVE=h2`，直接启动即可

#### 1.5.3 用户前台

新开一个终端：

```powershell
cd VernFlow\vernflow-web
npm install
npm run dev
```

默认访问地址：

- [http://localhost:5173](http://localhost:5173)

#### 1.5.4 管理员后台

再开一个终端：

```powershell
cd VernFlow\vernflow-admin
npm install
npm run dev
```

默认访问地址：

- [http://localhost:5174](http://localhost:5174)

### 1.6 登录账号

用户前台：

- `demo / demo123`
- `reviewer / reviewer123`

管理员后台：

- `admin / admin123`

### 1.7 完整模式：OpenClaw 助手引擎（可选）

游客对话助手默认运行在后端内置的 legacy 引擎上。如需完整助手功能（OpenClaw 插件 + Skills），按以下流程启动：

第一次使用先完成一次性安装（项目本地 Node 24、OpenClaw 2026.7.1、VernTrip 插件与 Skills）：

```powershell
.\scripts\openclaw\install.ps1
```

然后启动后端与 OpenClaw Gateway：

```powershell
.\scripts\openclaw\start-stack.ps1
```

说明：

- Gateway 监听 http://127.0.0.1:18789 ，健康检查 http://127.0.0.1:18789/healthz
- 后端以 `ASSISTANT_ENGINE=openclaw` 启动，Gateway 或模型不可用时自动降级，不影响其他功能
- 引擎可切换：`-AssistantEngine shadow`（旧助手为主，请求旁路影子采样对比）、`-AssistantEngine legacy`（无迁移回退旧助手）
- Gateway 令牌自动生成并保存在 `openclaw-runtime/state/runtime-secrets.json`，不进入仓库也不写入 `.env`

日常管理：

```powershell
.\scripts\openclaw\status.ps1          # 查看 Gateway 运行状态
.\scripts\openclaw\backend-status.ps1  # 查看后端运行状态
.\scripts\openclaw\stop-stack.ps1      # 停止后端与 Gateway
.\scripts\openclaw\stop.ps1            # 只停止 Gateway
```

完整施工与验收标准见 `docs/openclaw嵌入/OpenClaw嵌入施工方案.md`。

### 1.8 推荐启动顺序

最小联调建议按下面顺序启动：

1. `vernflow-ai`
2. `vernflow-backend`
3. `vernflow-web`
4. `vernflow-admin`

如果只是本地开发或比赛演示，先按这套流程跑通即可。

完整模式（含 OpenClaw）则按下面的顺序：

1. 完成 [1.7](#17-完整模式openclaw-助手引擎可选) 的一次性安装
2. 启动 `vernflow-ai`
3. 启动后端与 Gateway（`start-stack.ps1`）
4. 启动 `vernflow-web`
5. 启动 `vernflow-admin`

## 2. 配置文件怎么用

### 2.1 现在配置从哪里读取

本地直接启动时，项目统一读取**仓库根目录**的配置：

- `.env`
- `.env.local`

### 2.2 优先级怎么理解

优先级如下：

1. **系统环境变量 / 启动命令里显式传入的变量**
2. **根目录 `.env.local`**
3. **根目录 `.env`**
4. **代码中的默认值**

日常使用时可按以下规则理解：

- 日常改本机配置，改 `.env.local`
- 如果启动命令里手动设置了某个变量，那么它会覆盖 `.env.local`

### 2.3 `.env.example` 是做什么的

`.env.example` 只是模板，不会被程序自动读取。
你需要把它复制成 `.env.local` 才会生效：

```powershell
Copy-Item .env.example .env.local
```

### 2.4 最常用的配置项

以下是最常用的几个变量：

| 变量                      | 用途                   |
| ------------------------- | ---------------------- |
| `SERVER_PORT`           | 后端端口               |
| `VITE_API_PROXY_TARGET` | 前端开发代理转发地址   |
| `VITE_ADMIN_PORT`       | 管理后台开发端口       |
| `AI_SERVICE_BASE_URL`   | 后端访问 AI 服务的地址 |
| `DEEPSEEK_API_KEY`      | 大模型 API Key         |
| `DB_URL`                | 数据库连接             |
| `DB_USERNAME`           | 数据库用户名           |
| `DB_PASSWORD`           | 数据库密码             |

### 2.5 功能相关配置项

以下变量控制地图、助手引擎与账号行为，完整清单见 `.env.example`：

| 变量                                          | 用途                                    | 默认值                   |
| --------------------------------------------- | --------------------------------------- | ------------------------ |
| `ASSISTANT_ENGINE`                            | 游客对话助手引擎：`legacy` / `shadow` / `openclaw` | `legacy`        |
| `OPENCLAW_BASE_URL`                           | 后端访问 OpenClaw Gateway 的地址        | `http://127.0.0.1:18789` |
| `OPENCLAW_AGENT_ID`                           | OpenClaw Agent 标识                     | `verntrip-main`          |
| `RAG_PROFILE`                                 | RAG 检索配置名                          | `tfidf_topk_basic`       |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD`           | 管理后台登录账号                        | `admin` / `admin123`     |
| `USER_AUTH_REQUIRED`                          | 用户前台是否强制登录                    | `true`                   |
| `VITE_AMAP_JS_KEY` / `VITE_AMAP_SECURITY_CODE` | 高德地图 JS API Key 与安全密钥          | 空                       |
| `AMAP_WEB_SERVICE_KEY`                        | 高德 Web 服务 Key（按城市动态补充 POI） | 空                       |

## 3. 两种本地运行方式

### 3.1 方式一：H2 模式

适用场景：

- 本地快速联调
- 本地开发
- 快速演示

特点：

- 不需要安装 PostgreSQL
- 启动流程较短
- 适合快速完成本地联调

做法：

- 后端启动前设置 `SPRING_PROFILES_ACTIVE=h2`
- 数据库会使用本地 H2 文件

### 3.2 方式二：PostgreSQL 模式

适用场景：

- 想验证更接近正式环境的行为
- 想使用 pgvector 相关的 dense / hybrid 检索能力

这时需要：

- 安装 `PostgreSQL 16+`
- 安装 `pgvector`
- 修改根目录 `.env.local` 中的数据库配置
- 启动后端时**不要**再设置 `SPRING_PROFILES_ACTIVE=h2`

一个最常见的 PostgreSQL 配置示例如下：

```env
DB_URL=jdbc:postgresql://localhost:5432/vernflow
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_DRIVER=org.postgresql.Driver
```

## 4. AI 服务说明

### 4.1 没有 DeepSeek Key 能不能跑

可以。

如果没有配置：

```env
DEEPSEEK_API_KEY=
```

AI 服务仍然能启动，只是会走本地 fallback 路径。

### 4.2 为什么 AI 服务第一次启动比较慢

如果你使用的是默认 embedding 配置：

```env
RAG_EMBEDDING_BACKEND=sentence_transformers
RAG_EMBEDDING_MODEL=intfloat/multilingual-e5-small
```

第一次启动时，可能会下载模型，所以会比较慢。

如仅需先完成启动验证，可改为：

```env
RAG_EMBEDDING_BACKEND=hash
```

这样会更轻量。仓库脚本 `scripts/start-vernflow-ai-dev.cmd` 已默认使用该轻量模式。

### 4.3 AI 视频生成

游客端（景点详情"生成 AI 视频"）与商家端（智能内容工作室"AI 视频生成"）支持两种生成方式：

- **AI 成片（默认，免费）**：AI 读取景点已核验事实撰写分镜解说词（DeepSeek，无 Key 时按类型模板兜底）→ 神经语音合成配音（edge-tts）→ 真实图片素材运镜/转场/字幕自动剪辑合成（ffmpeg）。约 1 分钟出一条，零成本
- **可灵真 AI 视频（可选）**：在 [kling.ai/dev/api-key](https://kling.ai/dev/api-key) 申请 API Key 后填入根目录 `.env.local`：

```env
KLING_API_KEY=你的key
```

配置了 Key 时任务自动走可灵真动态视频（异步 1-5 分钟，720p 约 $0.112/秒）；未配置时自动走免费的 AI 成片。生成视频统一转存到 `MEDIA_DIR`（默认 `./storage/media`）并通过本地地址播放。

相关配置：`KLING_BASE_URL`（默认 `https://api-singapore.klingai.com`）、`KLING_TIMEOUT`（默认 60s）、`MEDIA_DIR`。AI 成片依赖 `edge-tts` 与 `imageio-ffmpeg`（已加入 `vernflow-ai/requirements.txt`），配音需要联网调用微软 TTS 服务，失败时自动降级为无配音版本。

## 5. Docker 方式

Docker Compose 现在可以统一启动：

- Vue 游客端与商家端页面（Nginx）
- Spring Boot 后端
- Python AI 服务
- PostgreSQL + pgvector
- OpenClaw Gateway、VernTrip 插件和 Skill

首次部署先生成独立环境文件：

```powershell
.\scripts\docker\init-env.ps1
```

然后填写 `deploy/.env` 中的 `DEEPSEEK_API_KEY` 和三项高德配置，再启动：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

Windows 也可以直接使用：

```powershell
.\scripts\docker\start.ps1
.\scripts\docker\status.ps1
```

默认访问地址为 `http://localhost`。Web 容器将 `/api` 反向代理到后端；OpenClaw Gateway、后端和数据库只绑定本机端口。

停止服务不会删除数据：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

完整操作、备份恢复和故障检查见 `docs/施工文档/Docker部署指南.md`。

## 6. 前端正式部署

### 6.1 先构建

用户前台：

```powershell
cd vernflow-web
npm install
npm run build
```

管理后台：

```powershell
cd ..\vernflow-admin
npm install
npm run build
```

构建产物分别在：

- `vernflow-web/dist`
- `vernflow-admin/dist`

### 6.2 为什么正式部署要配反向代理

前台和管理后台请求后端时，使用的是：

- `/api/...`

在开发环境里，Vite 已经自动把它代理到了后端。
但正式部署时，如果你只是把 `dist` 目录丢到静态服务器上，而**没有**配置反向代理，接口就会失败。

所以正式部署时，需要把：

- `/api`

转发到后端，例如：

- `http://127.0.0.1:8080`

### 6.3 推荐部署方式

推荐把前台和管理后台分别部署为两个站点，例如：

- `app.example.com`
- `admin.example.com`

后端与 AI 服务则部署在服务端，再由反向代理转发接口请求。

## 7. 常见问题

### 7.1 `gradlew` 很慢

第一次运行时会下载：

- Gradle Wrapper
- Maven 依赖

这是正常现象。

### 7.2 后端能启动，但访问 `http://localhost:8080` 是 404

这是正常现象。
后端没有做首页页面，接口主要通过 `/api/...` 提供服务。

### 7.3 前端能打开，但接口都失败

一般检查这三件事：

1. `vernflow-backend` 是否已启动
2. `vernflow-ai` 是否已启动
3. 前端当前代理目标是否正确

### 7.4 PostgreSQL 报 `vector` 相关错误

说明数据库环境没有正确提供 `pgvector`。
通常可直接使用仓库自带的 Docker Compose 环境。

### 7.5 游客对话助手功能不完整

快速启动流程（未启动 OpenClaw Gateway）时，后端运行在 legacy 旧助手引擎上，这是预期行为。
需要完整助手功能请按 [1.7](#17-完整模式openclaw-助手引擎可选) 完成 OpenClaw 安装与启动；Gateway 或模型不可用时后端会自动降级，不影响其他功能。

## 8. 常用命令

运行后端测试：

```powershell
.\gradlew :vernflow-backend:test --no-daemon --console=plain
```

检查 AI 服务健康状态：

```powershell
Invoke-WebRequest -Uri http://localhost:8000/ai/health -UseBasicParsing
```

OpenClaw 助手引擎（详见 [1.7](#17-完整模式openclaw-助手引擎可选)）：

```powershell
.\scripts\openclaw\install.ps1     # 一次性安装
.\scripts\openclaw\start-stack.ps1 # 启动后端 + Gateway
.\scripts\openclaw\status.ps1      # 查看 Gateway 状态
.\scripts\openclaw\stop-stack.ps1  # 停止后端 + Gateway
```

停止 Docker Compose：

```powershell
docker compose -f deploy/docker-compose.yml down
```
