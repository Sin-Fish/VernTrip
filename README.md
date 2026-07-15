# VernFlow

VernFlow 是一个面向资料工作区的可审计智能体系统。当前仓库包含 4 个主要部分：

- `vernflow-web`：用户前台
- `vernflow-admin`：管理员后台
- `vernflow-backend`：Spring Boot 后端
- `vernflow-ai`：FastAPI AI 子服务

建议先按本文的“快速启动”流程完成本地联调，再根据需要查看 PostgreSQL、Docker 和正式部署部分。

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

### 1.4 启动 AI 子服务

```powershell
cd vernflow-ai
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

启动后可访问：

- [http://localhost:8000/ai/health](http://localhost:8000/ai/health)

### 1.5 启动后端

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

### 1.6 启动用户前台

新开一个终端：

```powershell
cd VernFlow\vernflow-web
npm install
npm run dev
```

默认访问地址：

- [http://localhost:5173](http://localhost:5173)

### 1.7 启动管理员后台

再开一个终端：

```powershell
cd VernFlow\vernflow-admin
npm install
npm run dev
```

默认访问地址：

- [http://localhost:5174](http://localhost:5174)

### 1.8 登录账号

用户前台：

- `demo / demo123`
- `reviewer / reviewer123`

管理员后台：

- `admin / admin123`

### 1.9 推荐启动顺序

建议按下面顺序启动：

1. `vernflow-ai`
2. `vernflow-backend`
3. `vernflow-web`
4. `vernflow-admin`

如果只是本地开发或比赛演示，先按这套流程跑通即可。

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

这样会更轻量。

## 5. Docker 方式

如果你想一次启动数据库、后端和 AI 服务，可以使用：

```powershell
docker compose -f deploy/docker-compose.yml up --build
```

这条命令会启动：

- PostgreSQL
- AI 子服务
- 后端

### 5.1 这条命令不会启动什么

当前 `deploy/docker-compose.yml` **不会**启动：

- `vernflow-web`
- `vernflow-admin`

也就是说，Docker Compose 目前只负责：

- 数据库
- 后端
- AI 子服务

前端和管理后台仍然需要你：

1. 本地用 `npm run dev` 启动
2. 或者先构建再用静态服务器部署

### 5.2 Docker 配置从哪里来

这里要特别注意：

**本地直接启动时**，项目会读根目录 `.env` / `.env.local`。
**Docker Compose 启动时**，以 `deploy/docker-compose.yml` 里的配置为准。

因此，如果你发现：

- 本地直启没问题
- Docker 起来后配置不一样

优先检查：

- `deploy/docker-compose.yml`

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

## 8. 常用命令

运行后端测试：

```powershell
.\gradlew :vernflow-backend:test --no-daemon --console=plain
```

检查 AI 服务健康状态：

```powershell
Invoke-WebRequest -Uri http://localhost:8000/ai/health -UseBasicParsing
```

停止 Docker Compose：

```powershell
docker compose -f deploy/docker-compose.yml down
```
