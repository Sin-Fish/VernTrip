from __future__ import annotations

import math
import re
from collections import Counter

from ....core.text_utils import normalize_whitespace
from ..contracts.retriever import RetrievalCandidate
from ..schemas import RagChunk


HAN_SEGMENT_PATTERN = re.compile(r"[\u4e00-\u9fff]+")
LATIN_OR_DIGIT_PATTERN = re.compile(r"[a-z0-9][a-z0-9_\-./]{1,}")


class Bm25Retriever:
    retriever_id = "bm25_sparse"

    def __init__(self, *, k1: float = 1.5, b: float = 0.75) -> None:
        self.k1 = k1
        self.b = b

    def retrieve(self, question: str, chunks: list[RagChunk]) -> list[RetrievalCandidate]:
        if not chunks:
            return []

        tokenized_docs = [tokenize(self._compose_text(chunk)) for chunk in chunks]
        query_tokens = tokenize(question)
        if not query_tokens:
            return [RetrievalCandidate(chunk=chunk, score=0.0) for chunk in chunks]

        document_frequencies = Counter()
        for tokens in tokenized_docs:
            document_frequencies.update(set(tokens))

        doc_lengths = [len(tokens) for tokens in tokenized_docs]
        average_doc_length = sum(doc_lengths) / max(len(doc_lengths), 1)
        total_docs = len(tokenized_docs)

        ranked: list[RetrievalCandidate] = []
        for chunk, tokens, doc_length in zip(chunks, tokenized_docs, doc_lengths):
            token_counts = Counter(tokens)
            score = 0.0
            for term in query_tokens:
                tf = token_counts.get(term, 0)
                if tf == 0:
                    continue
                df = document_frequencies.get(term, 0)
                idf = math.log(1.0 + (total_docs - df + 0.5) / (df + 0.5))
                numerator = tf * (self.k1 + 1.0)
                denominator = tf + self.k1 * (
                    1.0 - self.b + self.b * (doc_length / average_doc_length if average_doc_length else 0.0)
                )
                score += idf * (numerator / denominator)
            ranked.append(RetrievalCandidate(chunk=chunk, score=score))

        ranked.sort(key=lambda candidate: candidate.score, reverse=True)
        return ranked

    def _compose_text(self, chunk: RagChunk) -> str:
        return normalize_whitespace(
            f"{chunk.paper_title} {chunk.section_title or ''} {chunk.chunk_text}"
        )


def tokenize(text: str) -> list[str]:
    normalized = normalize_whitespace(text).lower()
    tokens: list[str] = []

    for match in LATIN_OR_DIGIT_PATTERN.finditer(normalized):
        tokens.append(match.group(0))

    for han_segment in HAN_SEGMENT_PATTERN.findall(normalized):
        if len(han_segment) == 1:
            tokens.append(han_segment)
            continue
        tokens.append(han_segment)
        tokens.extend(han_segment[index:index + 2] for index in range(len(han_segment) - 1))

    return tokens
