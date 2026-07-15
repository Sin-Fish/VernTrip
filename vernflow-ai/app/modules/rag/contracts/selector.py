from __future__ import annotations

from typing import Protocol

from .retriever import RetrievalCandidate


class Selector(Protocol):
    selector_id: str

    def select(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        ...
