from __future__ import annotations

from ..contracts.retriever import RetrievalCandidate


class NoOpGraphStrategy:
    graph_strategy_id = "none"

    def rerank(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        del question
        return candidates
