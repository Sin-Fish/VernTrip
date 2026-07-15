from __future__ import annotations

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from ....core.text_utils import normalize_whitespace
from ..contracts.retriever import RetrievalCandidate


class MmrSelector:
    selector_id = "mmr"

    def __init__(
        self,
        *,
        limit: int = 6,
        candidate_pool: int = 18,
        lambda_weight: float = 0.7,
    ) -> None:
        self.limit = limit
        self.candidate_pool = max(limit, candidate_pool)
        self.lambda_weight = lambda_weight

    def select(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        positive_candidates = [candidate for candidate in candidates if candidate.score > 0]
        if not positive_candidates:
            return []

        pool = positive_candidates[: self.candidate_pool]
        if len(pool) <= self.limit:
            return pool

        documents = [
            normalize_whitespace(
                f"{candidate.chunk.paper_title} {candidate.chunk.section_title or ''} {candidate.chunk.chunk_text}"
            )
            for candidate in pool
        ]
        corpus = [normalize_whitespace(question)] + documents
        vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), min_df=1)
        tfidf_matrix = vectorizer.fit_transform(corpus)
        query_similarities = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:]).flatten()
        diversity_matrix = cosine_similarity(tfidf_matrix[1:], tfidf_matrix[1:])

        normalized_scores = normalize_scores([candidate.score for candidate in pool])
        normalized_query_similarities = normalize_scores(query_similarities.tolist())
        relevance_scores = [
            (normalized_scores[index] + normalized_query_similarities[index]) / 2.0
            for index in range(len(pool))
        ]

        selected_indices: list[int] = []
        remaining_indices = list(range(len(pool)))
        while remaining_indices and len(selected_indices) < self.limit:
            if not selected_indices:
                first_index = max(remaining_indices, key=lambda index: relevance_scores[index])
                selected_indices.append(first_index)
                remaining_indices.remove(first_index)
                continue

            best_index = None
            best_score = None
            for candidate_index in remaining_indices:
                max_similarity = max(
                    diversity_matrix[candidate_index][selected_index]
                    for selected_index in selected_indices
                )
                mmr_score = (
                    self.lambda_weight * relevance_scores[candidate_index]
                    - (1.0 - self.lambda_weight) * max_similarity
                )
                if best_score is None or mmr_score > best_score:
                    best_score = mmr_score
                    best_index = candidate_index

            if best_index is None:
                break

            selected_indices.append(best_index)
            remaining_indices.remove(best_index)

        return [pool[index] for index in selected_indices]


def normalize_scores(values: list[float]) -> list[float]:
    if not values:
        return []
    maximum = max(values)
    minimum = min(values)
    if maximum == minimum:
        return [1.0 for _ in values]
    return [(value - minimum) / (maximum - minimum) for value in values]
