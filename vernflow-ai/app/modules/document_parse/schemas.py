from __future__ import annotations

from pydantic import BaseModel, Field

from ..summary.schemas import SummaryResponse


class ParsePdfRequest(BaseModel):
    file_path: str


class ReferenceItem(BaseModel):
    raw_reference: str
    ref_title: str | None = None
    ref_authors: str | None = None
    ref_year: int | None = None


class ChunkItem(BaseModel):
    section_title: str | None = None
    page_no: int | None = None
    chunk_text: str
    embedding_id: str


class ParsePdfResponse(SummaryResponse):
    title: str
    authors: list[str] = Field(default_factory=list)
    publication_year: int | None = None
    venue: str | None = None
    abstract_text: str | None = None
    references: list[ReferenceItem] = Field(default_factory=list)
    chunks: list[ChunkItem] = Field(default_factory=list)
