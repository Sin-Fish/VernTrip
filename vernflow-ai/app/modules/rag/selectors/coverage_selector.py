from __future__ import annotations

from dataclasses import dataclass

from ....core.text_utils import extract_keywords, normalize_whitespace
from ..contracts.retriever import RetrievalCandidate
from ..retrievers.bm25_retriever import tokenize


@dataclass(frozen=True)
class CoverageCandidate:
    candidate: RetrievalCandidate
    covered_terms: set[str]
    source_key: str


class CoverageSelector:
    selector_id = "coverage_v1"

    def __init__(
        self,
        *,
        limit: int = 6,
        candidate_pool: int = 18,
        relevance_weight: float = 0.4,
        coverage_weight: float = 0.45,
        diversity_weight: float = 0.15,
    ) -> None:
        self.limit = limit
        self.candidate_pool = max(limit, candidate_pool)
        self.relevance_weight = relevance_weight
        self.coverage_weight = coverage_weight
        self.diversity_weight = diversity_weight

    def select(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        positive_candidates = [candidate for candidate in candidates if candidate.score > 0]
        if not positive_candidates:
            return []

        pool = positive_candidates[: self.candidate_pool]
        if len(pool) <= self.limit:
            return pool

        coverage_terms = self._coverage_terms(question)
        normalized_scores = normalize_scores([candidate.score for candidate in pool])
        expanded_candidates = [self._expand_candidate(candidate, coverage_terms) for candidate in pool]

        if not coverage_terms:
            return sorted(
                pool,
                key=lambda candidate: candidate.score,
                reverse=True,
            )[: self.limit]

        selected: list[RetrievalCandidate] = []
        covered_terms: set[str] = set()
        selected_sources: set[str] = set()
        remaining_indices = list(range(len(expanded_candidates)))

        while remaining_indices and len(selected) < self.limit:
            best_index = None
            best_score = None
            for index in remaining_indices:
                expanded = expanded_candidates[index]
                new_terms = expanded.covered_terms - covered_terms
                coverage_gain = len(new_terms) / len(coverage_terms) if coverage_terms else 0.0
                source_bonus = 1.0 if expanded.source_key not in selected_sources else 0.0
                combined_score = (
                    self.relevance_weight * normalized_scores[index]
                    + self.coverage_weight * coverage_gain
                    + self.diversity_weight * source_bonus
                )
                if best_score is None or combined_score > best_score:
                    best_score = combined_score
                    best_index = index

            if best_index is None:
                break

            chosen = expanded_candidates[best_index]
            selected.append(chosen.candidate)
            covered_terms.update(chosen.covered_terms)
            selected_sources.add(chosen.source_key)
            remaining_indices.remove(best_index)

            if covered_terms >= coverage_terms and len(selected) >= min(self.limit, 3):
                break

        if len(selected) < self.limit:
            for index in remaining_indices:
                selected.append(expanded_candidates[index].candidate)
                if len(selected) >= self.limit:
                    break

        return selected[: self.limit]

    def _coverage_terms(self, question: str) -> set[str]:
        keywords = set(extract_keywords(question, limit=8))
        keywords.update(token for token in tokenize(question) if len(token) >= 2)
        return {term for term in keywords if term}

    def _expand_candidate(
        self,
        candidate: RetrievalCandidate,
        coverage_terms: set[str],
    ) -> CoverageCandidate:
        chunk = candidate.chunk
        haystack = normalize_whitespace(
            f"{chunk.paper_title} {chunk.section_title or ''} {chunk.chunk_text}"
        ).lower()
        covered_terms = {term for term in coverage_terms if term in haystack}
        source_key = (chunk.paper_title or "").strip().lower() or "__unknown__"
        return CoverageCandidate(
            candidate=candidate,
            covered_terms=covered_terms,
            source_key=source_key,
        )


def normalize_scores(values: list[float]) -> list[float]:
    if not values:
        return []
    maximum = max(values)
    minimum = min(values)
    if maximum == minimum:
        return [1.0 for _ in values]
    return [(value - minimum) / (maximum - minimum) for value in values]
