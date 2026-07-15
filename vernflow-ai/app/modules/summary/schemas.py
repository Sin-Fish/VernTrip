from __future__ import annotations

from pydantic import BaseModel, Field


class SummaryRequest(BaseModel):
    title: str | None = None
    abstract_text: str | None = None
    introduction_text: str | None = None


class SummaryResponse(BaseModel):
    ai_summary: str
    summary_problem: str
    summary_method: str
    summary_conclusion: str
    summary_value: str
    keywords: list[str] = Field(default_factory=list)
