from __future__ import annotations

from ..contracts.retriever import RetrievalCandidate


class TopKSelector:
    def __init__(self, limit: int = 6) -> None:
        self.limit = limit
        self.selector_id = f"topk_{limit}"

    def select(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        del question
        if not candidates:
            return []
        return [candidate for candidate in candidates[: self.limit] if candidate.score > 0]
