from __future__ import annotations

from fastapi import APIRouter

from ...modules.rag.embeddings.local_dense_encoder import embed_texts_with_metadata
from ...modules.rag.schemas import EmbedTextsRequest, EmbedTextsResponse, EmbeddingItem


router = APIRouter()


@router.post("/ai/embed-texts", response_model=EmbedTextsResponse)
def embed_texts_endpoint(request: EmbedTextsRequest) -> EmbedTextsResponse:
    embedding_id, vectors = embed_texts_with_metadata(request.texts, input_type=request.input_type)
    items = [
        EmbeddingItem(embedding_id=embedding_id, vector=vector)
        for vector in vectors
    ]
    return EmbedTextsResponse(embeddings=items)
