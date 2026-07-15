from __future__ import annotations

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from ....core.text_utils import normalize_whitespace
from ..contracts.retriever import RetrievalCandidate
from ..schemas import RagChunk


class TfidfRetriever:
    retriever_id = "tfidf_charwb"

    def retrieve(self, question: str, chunks: list[RagChunk]) -> list[RetrievalCandidate]:
        if not chunks:
            return []

        corpus = [question] + [
            normalize_whitespace(f"{chunk.paper_title} {chunk.section_title or ''} {chunk.chunk_text}")
            for chunk in chunks
        ]
        vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), min_df=1)
        tfidf_matrix = vectorizer.fit_transform(corpus)
        scores = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:]).flatten()
        ranked_pairs = sorted(zip(chunks, scores), key=lambda pair: pair[1], reverse=True)
        return [RetrievalCandidate(chunk=chunk, score=float(score)) for chunk, score in ranked_pairs]
