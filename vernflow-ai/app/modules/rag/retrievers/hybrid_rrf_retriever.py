from __future__ import annotations

from ..contracts.retriever import RetrievalCandidate
from ..schemas import RagChunk
from .bm25_retriever import Bm25Retriever
from .tfidf_retriever import TfidfRetriever


class HybridRrfRetriever:
    retriever_id = "hybrid_rrf_tfidf_bm25"

    def __init__(self, *, rrf_k: int = 60) -> None:
        self.rrf_k = rrf_k
        self.tfidf_retriever = TfidfRetriever()
        self.bm25_retriever = Bm25Retriever()

    def retrieve(self, question: str, chunks: list[RagChunk]) -> list[RetrievalCandidate]:
        if not chunks:
            return []

        tfidf_ranked = self.tfidf_retriever.retrieve(question, chunks)
        bm25_ranked = self.bm25_retriever.retrieve(question, chunks)

        fused_scores: dict[int, float] = {index: 0.0 for index in range(len(chunks))}
        tfidf_order = {id(candidate.chunk): rank for rank, candidate in enumerate(tfidf_ranked, start=1)}
        bm25_order = {id(candidate.chunk): rank for rank, candidate in enumerate(bm25_ranked, start=1)}

        for index, chunk in enumerate(chunks):
            tfidf_rank = tfidf_order.get(id(chunk))
            bm25_rank = bm25_order.get(id(chunk))
            score = 0.0
            if tfidf_rank is not None:
                score += 1.0 / (self.rrf_k + tfidf_rank)
            if bm25_rank is not None:
                score += 1.0 / (self.rrf_k + bm25_rank)
            fused_scores[index] = score

        ranked = [
            RetrievalCandidate(chunk=chunk, score=fused_scores[index])
            for index, chunk in enumerate(chunks)
        ]
        ranked.sort(key=lambda candidate: candidate.score, reverse=True)
        return ranked
