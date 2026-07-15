from __future__ import annotations

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from ....core.text_utils import normalize_whitespace
from ..contracts.retriever import RetrievalCandidate


class RandomWalkRerankStrategy:
    graph_strategy_id = "random_walk_v1"

    def __init__(
        self,
        *,
        candidate_pool: int = 24,
        restart_probability: float = 0.35,
        score_blend: float = 0.65,
        max_iterations: int = 20,
        convergence_eps: float = 1e-6,
    ) -> None:
        self.candidate_pool = candidate_pool
        self.restart_probability = restart_probability
        self.score_blend = score_blend
        self.max_iterations = max_iterations
        self.convergence_eps = convergence_eps

    def rerank(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        del question
        positive_candidates = [candidate for candidate in candidates if candidate.score > 0]
        if len(positive_candidates) <= 1:
            return candidates

        graph_candidates = positive_candidates[: self.candidate_pool]
        remainder_candidates = positive_candidates[self.candidate_pool :]
        adjacency = self._adjacency(graph_candidates)
        if adjacency.shape[0] == 0:
            return candidates

        prior = normalize_scores([candidate.score for candidate in graph_candidates])
        transition = self._transition_matrix(adjacency)
        state = np.array(prior, dtype=float)
        restart = np.array(prior, dtype=float)

        for _ in range(self.max_iterations):
            updated = self.restart_probability * restart + (1.0 - self.restart_probability) * (transition.T @ state)
            if np.linalg.norm(updated - state, ord=1) <= self.convergence_eps:
                state = updated
                break
            state = updated

        blended = [
            self.score_blend * float(state[index]) + (1.0 - self.score_blend) * prior[index]
            for index in range(len(graph_candidates))
        ]
        reranked_candidates = self._with_rescored_candidates(graph_candidates, blended)
        reranked_candidates.sort(key=lambda candidate: candidate.score, reverse=True)

        non_positive = [candidate for candidate in candidates if candidate.score <= 0]
        return reranked_candidates + remainder_candidates + non_positive

    def _adjacency(self, candidates: list[RetrievalCandidate]) -> np.ndarray:
        documents = [
            normalize_whitespace(
                f"{candidate.chunk.paper_title or ''} {candidate.chunk.section_title or ''} {candidate.chunk.chunk_text}"
            )
            for candidate in candidates
        ]
        if not any(documents):
            return np.zeros((0, 0), dtype=float)

        vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), min_df=1)
        matrix = vectorizer.fit_transform(documents)
        adjacency = cosine_similarity(matrix)
        np.fill_diagonal(adjacency, 0.0)

        for row in range(len(candidates)):
            row_chunk = candidates[row].chunk
            for column in range(len(candidates)):
                if row == column:
                    continue
                col_chunk = candidates[column].chunk
                if (row_chunk.paper_title or "").strip().lower() == (col_chunk.paper_title or "").strip().lower():
                    adjacency[row, column] += 0.12
                if (row_chunk.section_title or "").strip().lower() == (col_chunk.section_title or "").strip().lower():
                    adjacency[row, column] += 0.05

        np.clip(adjacency, a_min=0.0, a_max=None, out=adjacency)
        return adjacency

    def _transition_matrix(self, adjacency: np.ndarray) -> np.ndarray:
        if adjacency.size == 0:
            return adjacency
        transition = adjacency.copy()
        row_sums = transition.sum(axis=1, keepdims=True)
        zero_rows = row_sums.squeeze(axis=1) == 0
        if np.any(zero_rows):
            transition[zero_rows, :] = 1.0
            row_sums = transition.sum(axis=1, keepdims=True)
        return transition / row_sums

    def _with_rescored_candidates(
        self,
        candidates: list[RetrievalCandidate],
        normalized_scores: list[float],
    ) -> list[RetrievalCandidate]:
        original_scores = [candidate.score for candidate in candidates]
        maximum = max(original_scores)
        minimum = min(original_scores)
        score_range = maximum - minimum
        rescored: list[RetrievalCandidate] = []
        for index, candidate in enumerate(candidates):
            if score_range <= 0:
                blended_score = candidate.score
            else:
                blended_score = minimum + normalized_scores[index] * score_range
            rescored.append(RetrievalCandidate(chunk=candidate.chunk, score=float(blended_score)))
        return rescored


def normalize_scores(values: list[float]) -> list[float]:
    if not values:
        return []
    total = float(sum(values))
    if total <= 0:
        return [1.0 / len(values) for _ in values]
    return [float(value) / total for value in values]
