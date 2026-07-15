from __future__ import annotations

from .pdf_parser import parse_pdf_document


def parse_pdf(file_path: str) -> dict:
    return parse_pdf_document(file_path)
