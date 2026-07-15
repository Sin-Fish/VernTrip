from __future__ import annotations

from typing import Protocol

from ..schemas import ConversationMessage, RagEvidence


class Generator(Protocol):
    generator_id: str

    def generate(
        self,
        question: str,
        scope: str,
        evidences: list[RagEvidence],
        *,
        evidence_found: bool,
        conversation_context: list[ConversationMessage] | None = None,
    ) -> str | None:
        ...
