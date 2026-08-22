# VernTrip Docker 部署指南

## 1. 部署范围

主编排文件为 `deploy/docker-compose.yml`，包含以下服务：

| 服务 | 容器端口 | 默认宿主机端口 | 说明 |
| --- | ---: | ---: | --- |
| `web` | 80 | 80 | Vue 构建产物与 `/api` 反向代理 |
| `backend` | 8080 | 127.0.0.1:8080 | Spring Boot 业务服务 |
| `ai-service` | 8000 | 不公开 | RAG 与基础 AI 子服务 |
| `postgres` | 5432 | 127.0.0.1:5432 | PostgreSQL 16 与 pgvector |
| `openclaw-gateway` | 18789 | 127.0.0.1:18789 | OpenClaw Agent Runtime |
| `openclaw-init` | - | - | 首次启动时安装插件并初始化配置 |

浏览器只访问 `web`，不会持有 OpenClaw Gateway Token。OpenClaw 通过内部网络调用 Spring 后端的受控工具接口。

## 2. 环境要求

- Docker Engine 24 或更高版本。
- Docker Compose v2.20 或更高版本。
- 首次构建需要访问 npm、PyPI、Maven Central 和 GitHub Container Registry。
- 建议预留至少 8 GB 内存、15 GB 可用磁盘空间。

首次构建 Python AI 镜像和 OpenClaw 镜像耗时较长，属于正常情况。

## 3. 初始化配置

在项目根目录执行：

```powershell
.\scripts\docker\init-env.ps1
```

如果根目录 `.env` 或 `.env.local` 已配置 DeepSeek 与高德密钥，可执行：

```powershell
.\scripts\docker\init-env.ps1 -ImportLocalSecrets
```

脚本根据 `deploy/.env.example` 创建 `deploy/.env`，并自动生成：

- PostgreSQL 密码。
- 后台管理员密码。
- OpenClaw Gateway Token。
- VernTrip OpenClaw 内部服务 Token。

随后编辑 `deploy/.env`，至少填写：

```env
DEEPSEEK_API_KEY=你的DeepSeek密钥
VITE_AMAP_JS_KEY=高德Web端JS_API_Key
VITE_AMAP_SECURITY_CODE=高德Web端安全密钥
AMAP_WEB_SERVICE_KEY=高德Web服务Key
```

`deploy/.env` 已被 Git 忽略，不得将其中内容提交到仓库。

## 4. 启动与检查

如果开发机上已经运行本地前端、后端、PostgreSQL 或 OpenClaw，请先停止占用 `80`、`8080`、`5432`、`18789` 端口的进程，或在 `deploy/.env` 中修改对应宿主机端口。

构建并后台启动完整系统：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

查看服务和健康状态：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

Windows 环境可使用封装脚本完成相同操作：

```powershell
.\scripts\docker\start.ps1
.\scripts\docker\status.ps1
```

正常情况下，`postgres`、`ai-service`、`backend`、`openclaw-gateway` 和 `web` 均显示 `healthy`，`openclaw-init` 显示成功退出。

访问地址：

- 系统页面：`http://localhost`
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`
- OpenClaw 健康检查：`http://127.0.0.1:18789/healthz`

## 5. 日志与诊断

查看全栈日志：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f --tail 200
```

只查看核心链路：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f backend openclaw-init openclaw-gateway
```

检查 OpenClaw 配置和插件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile tools run --rm openclaw-cli config validate
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile tools run --rm openclaw-cli plugins inspect verntrip --runtime --json
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile tools run --rm openclaw-cli agents list --json
```

## 6. 停止、更新与重建

停止服务并保留数据库和文件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

Windows 环境也可以执行：

```powershell
.\scripts\docker\stop.ps1
```

更新代码后重新构建：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml build --pull
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
```

仅重建业务服务：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build web backend
```

删除容器和全部数据卷会永久清空数据库、上传文件和 OpenClaw 状态，只能在确认无需保留数据时执行：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down --volumes
```

## 7. 数据备份与恢复

备份 PostgreSQL：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/verntrip.dump'
docker compose --env-file deploy/.env -f deploy/docker-compose.yml cp postgres:/tmp/verntrip.dump ./verntrip.dump
```

恢复前应先确认目标数据库允许覆盖：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml cp ./verntrip.dump postgres:/tmp/verntrip.dump
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists /tmp/verntrip.dump'
```

`backend-storage` 与 `openclaw-state` 为 Docker 命名卷。正式部署建议使用宿主机或平台提供的卷快照能力定期备份。

## 8. 部署约束

- DeepSeek 和高德密钥只通过环境变量注入。
- OpenClaw Gateway 默认只绑定宿主机回环地址，不应直接暴露到公网。
- PostgreSQL 默认只绑定宿主机回环地址，正式服务器可进一步删除端口映射。
- 对公网部署时，应在 Web 容器前增加 HTTPS 反向代理或云负载均衡。
- 修改高德 Web 端 Key 或安全密钥后必须重新构建 `web` 镜像，因为 Vite 会在构建阶段写入这些配置。
