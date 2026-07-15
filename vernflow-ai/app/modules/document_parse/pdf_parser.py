from __future__ import annotations

import re
import sys
from pathlib import Path

from ...core.text_utils import normalize_whitespace
from ..summary.service import build_summary


SECTION_HEADINGS = {
    "abstract",
    "keywords",
    "introduction",
    "related work",
    "background",
    "method",
    "methods",
    "approach",
    "experiment",
    "experiments",
    "evaluation",
    "discussion",
    "conclusion",
    "references",
}

VENUE_KEYWORDS = (
    "conference",
    "journal",
    "proceedings",
    "symposium",
    "workshop",
    "transactions",
    "letters",
    "ieee",
    "acm",
)


def parse_pdf_document(file_path: str) -> dict:
    pdf_path = Path(file_path)
    if not pdf_path.exists():
        raise FileNotFoundError(f"Could not find PDF file: {file_path}")

    _ensure_pypdf_available()
    from pypdf import PdfReader

    reader = PdfReader(str(pdf_path))
    page_texts = [_normalize_page_text(page.extract_text() or "") for page in reader.pages]

    first_page_text = page_texts[0] if page_texts else ""
    full_text = "\n".join(page_texts)
    abstract_text = extract_abstract("\n".join(page_texts[:2]))
    introduction_text = extract_introduction("\n".join(page_texts[:3]))
    title = extract_title(first_page_text, pdf_path)
    authors = extract_authors(first_page_text, title)
    publication_year = extract_year(first_page_text) or extract_year(full_text)
    references = extract_references(full_text)
    chunks = build_chunks(page_texts)
    summary = build_summary(title, abstract_text, introduction_text)

    return {
        "title": title,
        "authors": authors,
        "publication_year": publication_year,
        "venue": extract_venue(first_page_text, authors),
        "abstract_text": abstract_text or None,
        "references": references,
        "chunks": chunks,
        **summary,
    }


def _ensure_pypdf_available() -> None:
    try:
        __import__("pypdf")
    except ModuleNotFoundError:
        bundled_site_packages = Path(
            r"C:\Users\linli\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\Lib\site-packages"
        )
        if bundled_site_packages.exists():
            sys.path.append(str(bundled_site_packages))
        __import__("pypdf")


def _normalize_page_text(text: str) -> str:
    lines = [normalize_whitespace(line) for line in text.replace("\r", "\n").split("\n")]
    cleaned_lines: list[str] = []
    previous_blank = False

    for line in lines:
        if not line:
            if not previous_blank:
                cleaned_lines.append("")
            previous_blank = True
            continue
        cleaned_lines.append(line)
        previous_blank = False

    return "\n".join(cleaned_lines).strip()


def extract_title(first_page_text: str, pdf_path: Path) -> str:
    candidate_lines = [line.strip() for line in first_page_text.splitlines() if line.strip()]

    for line in candidate_lines[:12]:
        lowered_line = line.lower()
        if lowered_line in SECTION_HEADINGS:
            continue
        if any(word in lowered_line for word in ("doi", "http", "@")):
            continue
        if 8 <= len(line) <= 220:
            return line

    return pdf_path.stem.replace("_", " ")


def extract_authors(first_page_text: str, title: str) -> list[str]:
    lines = [line.strip() for line in first_page_text.splitlines() if line.strip()]

    try:
        title_index = lines.index(title)
    except ValueError:
        title_index = 0

    author_lines: list[str] = []
    for line in lines[title_index + 1 : title_index + 6]:
        lowered_line = line.lower()
        if lowered_line in SECTION_HEADINGS:
            break
        if any(keyword in lowered_line for keyword in VENUE_KEYWORDS):
            break
        if "@" in line or len(line) > 160:
            continue
        author_lines.append(line)

    authors_text = normalize_whitespace(" ".join(author_lines))
    authors = [
        author.strip()
        for author in re.split(r",| and |;|、|，", authors_text)
        if author.strip() and len(author.strip()) <= 80
    ]
    return authors[:8]


def extract_year(text: str) -> int | None:
    for year_text in re.findall(r"\b(19\d{2}|20\d{2})\b", text):
        year_value = int(year_text)
        if 1990 <= year_value <= 2035:
            return year_value
    return None


def extract_venue(first_page_text: str, authors: list[str]) -> str | None:
    candidate_lines = [line.strip() for line in first_page_text.splitlines() if line.strip()]
    author_set = {normalize_whitespace(author).lower() for author in authors if author.strip()}

    for line in candidate_lines:
        normalized_line = normalize_whitespace(line)
        lowered_line = normalized_line.lower()
        if lowered_line in author_set:
            continue
        if len(normalized_line) > 140 or len(normalized_line.split()) > 20:
            continue
        if any(author_name in lowered_line for author_name in author_set if len(author_name) >= 4):
            continue
        if any(keyword in lowered_line for keyword in VENUE_KEYWORDS):
            return normalized_line

    return None


def extract_abstract(text: str) -> str:
    match = re.search(
        r"(?:^|\n)\s*abstract\s*[:\-]?\s*(.+?)(?=\n\s*(keywords|introduction|1\.|i\.)\b)",
        text,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not match:
        return ""
    return normalize_whitespace(match.group(1))


def extract_introduction(text: str) -> str:
    match = re.search(
        r"(?:^|\n)\s*introduction\s*[:\-]?\s*(.+?)(?=\n\s*(2\.|ii\.|related work|background|method|methods)\b)",
        text,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not match:
        return ""
    return normalize_whitespace(match.group(1))


def extract_references(full_text: str) -> list[dict]:
    match = re.search(
        r"(?:\n|^)\s*references\s*[:\-]?\s*(.+)$",
        full_text,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not match:
        return []

    reference_block = match.group(1).strip()
    lines = [line.strip() for line in reference_block.splitlines() if line.strip()]
    entries: list[str] = []
    buffer: list[str] = []

    for line in lines:
        is_new_entry = bool(re.match(r"^\[?\d+\]?[\.\)]?\s+", line))
        if is_new_entry and buffer:
            entries.append(normalize_whitespace(" ".join(buffer)))
            buffer = [line]
        else:
            buffer.append(line)

    if buffer:
        entries.append(normalize_whitespace(" ".join(buffer)))

    return [parse_reference(entry) for entry in entries[:50] if entry]


def parse_reference(raw_reference: str) -> dict:
    year_match = re.search(r"\b(19\d{2}|20\d{2})\b", raw_reference)
    ref_year = int(year_match.group(1)) if year_match else None

    title_match = re.search(r"[\"“”](.+?)[\"“”]", raw_reference)
    if title_match:
        ref_title = title_match.group(1).strip()
        ref_authors = raw_reference.split(title_match.group(0))[0].strip(" .")
    else:
        segments = [segment.strip() for segment in re.split(r"\.\s+", raw_reference) if segment.strip()]
        ref_authors = segments[0] if segments else ""
        title_candidates = [segment for segment in segments[1:4] if 6 <= len(segment) <= 220]
        ref_title = max(title_candidates, key=len) if title_candidates else ""

    return {
        "raw_reference": raw_reference,
        "ref_title": ref_title or None,
        "ref_authors": ref_authors or None,
        "ref_year": ref_year,
    }


def build_chunks(page_texts: list[str]) -> list[dict]:
    chunks: list[dict] = []
    chunk_index = 0

    for page_number, page_text in enumerate(page_texts, start=1):
        lines = [normalize_whitespace(line) for line in page_text.splitlines() if normalize_whitespace(line)]
        section_title = "Body"
        current_buffer: list[str] = []

        for line in lines:
            if _looks_like_heading(line):
                if current_buffer:
                    chunks.append(_build_chunk(section_title, page_number, current_buffer, chunk_index))
                    chunk_index += 1
                    current_buffer = []
                section_title = line
                continue

            current_buffer.append(line)
            if len(" ".join(current_buffer)) >= 900:
                chunks.append(_build_chunk(section_title, page_number, current_buffer, chunk_index))
                chunk_index += 1
                current_buffer = []

        if current_buffer:
            chunks.append(_build_chunk(section_title, page_number, current_buffer, chunk_index))
            chunk_index += 1

    return chunks


def _build_chunk(section_title: str, page_number: int, lines: list[str], chunk_index: int) -> dict:
    return {
        "section_title": section_title or "Body",
        "page_no": page_number,
        "chunk_text": normalize_whitespace(" ".join(lines)),
        "embedding_id": f"chunk-{page_number}-{chunk_index}",
    }


def _looks_like_heading(line: str) -> bool:
    normalized_line = normalize_whitespace(line)
    if not normalized_line:
        return False

    lowered_line = normalized_line.lower()
    if lowered_line in SECTION_HEADINGS:
        return True

    if len(normalized_line) > 80:
        return False

    if re.match(r"^(\d+(\.\d+){0,2}|[IVX]+)\s+[A-Z]", normalized_line):
        return True

    return normalized_line.isupper() and len(normalized_line.split()) <= 10
