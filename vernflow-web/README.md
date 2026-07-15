# VernFlow Web

## Environment

The web app now reads shared environment variables from the project root:

- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env`
- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env.local`

`VITE_API_PROXY_TARGET` can be configured there.

基于 `docs/前端原型/vernflow_frontend_v3.html` 落地的前端工程。

## 开发

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

当前阶段先使用本地 mock 数据对齐交互和信息架构，后续再逐步接入 `vernflow-backend` 与 `vernflow-ai`。
