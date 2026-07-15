from __future__ import annotations

from fastapi import APIRouter

from ...modules.rag.schemas import (
    RagQueryRequest,
    RagQueryResponse,
    RagRerankQueryRequest,
    RagRerankRetrieveRequest,
    RagRetrieveRequest,
    RagRetrieveResponse,
)
from ...modules.rag.service import (
    run_rag,
    run_rag_from_candidates,
    run_retrieve,
    run_retrieve_from_candidates,
)


router = APIRouter()


@router.post("/ai/rag-query", response_model=RagQueryResponse)
def rag_query_endpoint(request: RagQueryRequest) -> RagQueryResponse:
    response_payload = run_rag(
        request.question,
        request.scope,
        request.chunks,
        request.profile_id,
        request.conversation_context,
    )
    return RagQueryResponse(**response_payload)


@router.post("/ai/rag-retrieve", response_model=RagRetrieveResponse)
def rag_retrieve_endpoint(request: RagRetrieveRequest) -> RagRetrieveResponse:
    response_payload = run_retrieve(request.question, request.chunks, request.profile_id)
    return RagRetrieveResponse(**response_payload)


@router.post("/ai/rag-rerank-query", response_model=RagQueryResponse)
def rag_rerank_query_endpoint(request: RagRerankQueryRequest) -> RagQueryResponse:
    response_payload = run_rag_from_candidates(
        request.question,
        request.scope,
        request.candidates,
        request.profile_id,
        request.conversation_context,
    )
    return RagQueryResponse(**response_payload)


@router.post("/ai/rag-rerank-retrieve", response_model=RagRetrieveResponse)
def rag_rerank_retrieve_endpoint(request: RagRerankRetrieveRequest) -> RagRetrieveResponse:
    response_payload = run_retrieve_from_candidates(
        request.question,
        request.candidates,
        request.profile_id,
    )
    return RagRetrieveResponse(**response_payload)
