# VernFlow AI Service

## Environment

The AI service now reads shared environment variables from the project root:

- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env`
- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env.local`

Service-local `.env.local` is no longer treated as a live config source.

这是 VernFlow 的 AI 子服务，负责：

- PDF 文本解析与基础元数据提取
- 阅读摘要与结构化梗概生成
- 论文切片构建
- 证据驱动问答

当前实现是本地可运行的 MVP 版本：

- PDF 解析使用 `pypdf`
- 检索与证据排序使用 `scikit-learn` 的 TF-IDF
- 摘要与梗概先使用规则与抽取式逻辑生成

当前目录已经按施工文档方向整理为：

- `app/api/v1/`：HTTP 路由入口
- `app/modules/document_parse/`：文档解析
- `app/modules/rag/`：检索问答
- `app/modules/summary/`：摘要生成
- `app/core/`：共享工具与核心能力

后续如果接入真实大模型和向量库，主要替换 `app/modules` 下的实现即可，不需要重做 Spring Boot 接口契约。
