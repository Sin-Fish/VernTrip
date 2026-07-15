from __future__ import annotations

import logging

from fastapi import FastAPI

from .api.v1.document_parse import router as document_parse_router
from .api.v1.embeddings import router as embeddings_router
from .api.v1.planning import router as planning_router
from .api.v1.rag import router as rag_router
from .api.v1.summary import router as summary_router
from .core.settings import get_settings
from .modules.rag.embeddings.local_dense_encoder import embed_texts_with_metadata


logger = logging.getLogger(__name__)


app = FastAPI(
    title="VernFlow AI Service",
    version="0.1.0",
    description="VernFlow 的 AI 子服务，提供文档解析、向量化与基于证据的问答能力。",
)


@app.on_event("startup")
def warmup_embedding_backend() -> None:
    settings = get_settings()
    if settings.rag_embedding_backend == "hash":
        logger.info("Skipping embedding warmup because hash backend is enabled.")
        return
    try:
        embedding_id, _ = embed_texts_with_metadata(["warmup"], input_type="query")
        logger.info("Embedding backend warmed up with %s.", embedding_id)
    except Exception:
        logger.exception("Failed to warm up embedding backend. The service will fall back at request time.")


@app.get("/ai/health")
def health_check() -> dict[str, str | bool]:
    settings = get_settings()
    return {
        "status": "ok",
        "provider": "deepseek" if settings.deepseek_enabled else "local-fallback",
        "model": settings.deepseek_model,
        "deepseek_enabled": settings.deepseek_enabled,
        "embedding_backend": settings.rag_embedding_backend,
        "embedding_model": settings.rag_embedding_model,
    }


app.include_router(document_parse_router)
app.include_router(summary_router)
app.include_router(rag_router)
app.include_router(planning_router)
app.include_router(embeddings_router)
