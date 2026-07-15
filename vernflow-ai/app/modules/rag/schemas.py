from __future__ import annotations

from pydantic import BaseModel, Field


class RagChunk(BaseModel):
    paper_id: int
    paper_title: str
    section_title: str | None = None
    page_no: int | None = None
    chunk_text: str


class ConversationMessage(BaseModel):
    role: str
    text: str


class RagQueryRequest(BaseModel):
    question: str
    scope: str
    chunks: list[RagChunk] = Field(default_factory=list)
    profile_id: str | None = None
    conversation_context: list[ConversationMessage] = Field(default_factory=list)


class RagRetrieveRequest(BaseModel):
    question: str
    chunks: list[RagChunk] = Field(default_factory=list)
    profile_id: str | None = None


class RagScoredChunk(BaseModel):
    paper_id: int
    paper_title: str
    section_title: str | None = None
    page_no: int | None = None
    chunk_text: str
    score: float


class RagRerankQueryRequest(BaseModel):
    question: str
    scope: str
    candidates: list[RagScoredChunk] = Field(default_factory=list)
    profile_id: str | None = None
    conversation_context: list[ConversationMessage] = Field(default_factory=list)


class RagRerankRetrieveRequest(BaseModel):
    question: str
    candidates: list[RagScoredChunk] = Field(default_factory=list)
    profile_id: str | None = None


class RagEvidence(BaseModel):
    paper_id: int
    paper_title: str
    section_title: str | None = None
    page_no: int | None = None
    excerpt: str
    score: float


class RagProfileMetadata(BaseModel):
    profile_id: str
    retriever_id: str
    selector_id: str
    graph_strategy_id: str
    generator_id: str


class RagQueryResponse(BaseModel):
    answer: str
    evidences: list[RagEvidence] = Field(default_factory=list)
    profile: RagProfileMetadata


class RagRetrieveResponse(BaseModel):
    evidences: list[RagEvidence] = Field(default_factory=list)
    profile: RagProfileMetadata


class EmbedTextsRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)
    input_type: str = "passage"


class EmbeddingItem(BaseModel):
    embedding_id: str
    vector: list[float] = Field(default_factory=list)


class EmbedTextsResponse(BaseModel):
    embeddings: list[EmbeddingItem] = Field(default_factory=list)
