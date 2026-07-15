from __future__ import annotations

from fastapi import APIRouter

from ...modules.summary.schemas import SummaryRequest, SummaryResponse
from ...modules.summary.service import build_summary


router = APIRouter()


@router.post("/ai/summarize", response_model=SummaryResponse)
def summarize_endpoint(request: SummaryRequest) -> SummaryResponse:
    summary_payload = build_summary(request.title, request.abstract_text, request.introduction_text)
    return SummaryResponse(**summary_payload)
