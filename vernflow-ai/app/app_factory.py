from __future__ import annotations

import logging
from collections.abc import Iterable

from fastapi import APIRouter, FastAPI

from .core.settings import get_settings
from .modules.rag.embeddings.local_dense_encoder import embed_texts_with_metadata


logger = logging.getLogger(__name__)


def create_ai_app(
    *,
    title: str,
    description: str,
    routers: Iterable[APIRouter],
    service_kind: str,
    warmup_embeddings: bool = False,
) -> FastAPI:
    app = FastAPI(
        title=title,
        version="0.1.0",
        description=description,
    )

    @app.on_event("startup")
    def warmup_embedding_backend() -> None:
        if not warmup_embeddings:
            return
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
            "service_kind": service_kind,
            "provider": "deepseek" if settings.deepseek_enabled else "local-fallback",
            "model": settings.deepseek_model,
            "deepseek_enabled": settings.deepseek_enabled,
            "embedding_backend": settings.rag_embedding_backend,
            "embedding_model": settings.rag_embedding_model,
        }

    for router in routers:
        app.include_router(router)

    return app
