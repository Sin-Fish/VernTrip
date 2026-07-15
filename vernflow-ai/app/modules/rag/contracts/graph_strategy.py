from __future__ import annotations

from typing import Protocol

from .retriever import RetrievalCandidate


class GraphStrategy(Protocol):
    graph_strategy_id: str

    def rerank(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        ...
