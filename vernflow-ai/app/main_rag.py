from __future__ import annotations

from .api.v1.document_parse import router as document_parse_router
from .api.v1.embeddings import router as embeddings_router
from .api.v1.rag import router as rag_router
from .api.v1.summary import router as summary_router
from .app_factory import create_ai_app


app = create_ai_app(
    title="VernFlow RAG Service",
    description="VernFlow 的 RAG 服务，负责文档解析、向量化、检索、重排与问答生成。",
    routers=[document_parse_router, summary_router, rag_router, embeddings_router],
    service_kind="rag",
    warmup_embeddings=True,
)
