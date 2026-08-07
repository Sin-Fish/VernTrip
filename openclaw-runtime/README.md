# VernTrip OpenClaw Runtime

该目录保存 VernTrip 自有的 OpenClaw 插件、Skills、配置样例和自定义镜像，不保存 OpenClaw 上游源码。

## 当前能力

- OpenClaw 版本固定为 `2026.7.1`。
- `verntrip` 原生插件提供运行诊断、受信任工作区读取和文旅资源目录查询工具。
- `verntrip-foundation` Skill 定义身份、权限和证据安全边界。
- 自定义镜像在构建时编译并打包插件，由初始化服务通过 `npm-pack:` 安装到 OpenClaw 受管目录。
- OpenClaw 状态、真实配置和密钥由运行环境持久化，不进入仓库。

## 环境要求

- 推荐 Docker Desktop + Docker Compose v2。
- 不使用 Docker 时，需要 Node `22.22.3+`、Node `24.15+` 或 Node `25.9+`。
- 本地一体化启动脚本默认使用 OpenClaw 作为游客对话主引擎；Gateway 或模型不可用时由 Spring 服务自动降级。

## 插件开发检查

在兼容 Node 环境中进入 `openclaw-runtime/plugin` 后执行：

```powershell
npm install
npm run check
npm run build
npm run pack:check
```

安装到 OpenClaw 后，使用以下命令确认运行时发现结果：

```powershell
openclaw plugins inspect verntrip --runtime --json
openclaw skills check
```

## 容器接入

`deploy/docker-compose.openclaw.yml` 是现有部署的叠加文件。首次启动前需要完成 OpenClaw onboarding，并根据 `config/openclaw.example.json5` 设置插件路径、Agent、Skill 和 OpenResponses 端点。

```powershell
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml build openclaw-gateway
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml run --rm --no-deps --entrypoint node openclaw-gateway dist/index.js onboard --mode local --no-install-daemon
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml run --rm --no-deps --entrypoint node openclaw-gateway dist/index.js config set --batch-file /opt/verntrip/config/verntrip.config.batch.json
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml run --rm --no-deps --entrypoint node openclaw-gateway dist/index.js agents add verntrip-main --workspace /home/node/.openclaw/workspace-verntrip --non-interactive
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml up -d openclaw-plugin-init openclaw-gateway
```

`openclaw-plugin-init` 会把镜像内的插件包安装到共享状态卷，并在成功后退出；Gateway 仅在初始化成功后启动。Gateway 只向本机 `127.0.0.1` 发布调试端口，容器内的 Spring 后端通过 `http://openclaw-gateway:18789` 访问。

启动后使用 CLI 容器完成运行验收：

```powershell
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml run --rm openclaw-cli plugins inspect verntrip --runtime --json
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.openclaw.yml run --rm openclaw-cli skills check
```

完整阶段和验收标准见 `docs/openclaw嵌入/OpenClaw嵌入施工方案.md`。
