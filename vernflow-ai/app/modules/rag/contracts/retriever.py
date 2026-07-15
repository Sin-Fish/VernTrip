from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from ..schemas import RagChunk


@dataclass(frozen=True)
class RetrievalCandidate:
    chunk: RagChunk
    score: float


class Retriever(Protocol):
    retriever_id: str

    def retrieve(self, question: str, chunks: list[RagChunk]) -> list[RetrievalCandidate]:
        ...
