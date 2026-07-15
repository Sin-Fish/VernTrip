from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ...modules.document_parse.schemas import ParsePdfRequest, ParsePdfResponse
from ...modules.document_parse.service import parse_pdf


router = APIRouter()


@router.post("/ai/parse-pdf", response_model=ParsePdfResponse)
def parse_pdf_endpoint(request: ParsePdfRequest) -> ParsePdfResponse:
    try:
        parsed_payload = parse_pdf(request.file_path)
        return ParsePdfResponse(**parsed_payload)
    except FileNotFoundError as exception:
        raise HTTPException(status_code=404, detail=str(exception)) from exception
    except Exception as exception:
        raise HTTPException(status_code=500, detail=f"PDF 解析失败：{exception}") from exception
