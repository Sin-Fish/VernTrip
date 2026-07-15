from __future__ import annotations

from ..contracts.retriever import RetrievalCandidate
from ..schemas import RagChunk


class BackendDenseRetriever:
    retriever_id = "dense_pgvector_backend"

    def retrieve(self, question: str, chunks: list[RagChunk]) -> list[RetrievalCandidate]:
        del question, chunks
        return []
