from __future__ import annotations

from dataclasses import dataclass
from math import log1p

from ....core.text_utils import extract_keywords, normalize_whitespace
from ..contracts.retriever import RetrievalCandidate
from ..retrievers.bm25_retriever import tokenize


@dataclass(frozen=True)
class SubmodularFeatures:
    candidate: RetrievalCandidate
    query_terms: set[str]
    concept_terms: set[str]
    source_key: str
    section_key: str


class SubmodularCoverageSelector:
    selector_id = "submodular_coverage_v1"

    def __init__(
        self,
        *,
        limit: int = 6,
        candidate_pool: int = 20,
        relevance_weight: float = 0.35,
        query_coverage_weight: float = 0.35,
        concept_coverage_weight: float = 0.2,
        source_diversity_weight: float = 0.1,
    ) -> None:
        self.limit = limit
        self.candidate_pool = max(limit, candidate_pool)
        self.relevance_weight = relevance_weight
        self.query_coverage_weight = query_coverage_weight
        self.concept_coverage_weight = concept_coverage_weight
        self.source_diversity_weight = source_diversity_weight

    def select(self, question: str, candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
        positive_candidates = [candidate for candidate in candidates if candidate.score > 0]
        if not positive_candidates:
            return []

        pool = positive_candidates[: self.candidate_pool]
        if len(pool) <= self.limit:
            return pool

        query_terms = self._query_terms(question)
        if not query_terms:
            return pool[: self.limit]

        normalized_scores = normalize_scores([candidate.score for candidate in pool])
        feature_rows = [self._expand_candidate(candidate, query_terms) for candidate in pool]
        global_concepts = self._global_concepts(feature_rows)

        selected: list[RetrievalCandidate] = []
        covered_query_terms: set[str] = set()
        concept_counts: dict[str, int] = {}
        source_counts: dict[str, int] = {}
        remaining_indices = list(range(len(feature_rows)))

        while remaining_indices and len(selected) < self.limit:
            best_index = None
            best_gain = None
            for index in remaining_indices:
                features = feature_rows[index]
                marginal_gain = self._marginal_gain(
                    features,
                    normalized_scores[index],
                    query_terms,
                    global_concepts,
                    covered_query_terms,
                    concept_counts,
                    source_counts,
                )
                if best_gain is None or marginal_gain > best_gain:
                    best_gain = marginal_gain
                    best_index = index

            if best_index is None:
                break

            chosen = feature_rows[best_index]
            selected.append(chosen.candidate)
            covered_query_terms.update(chosen.query_terms)
            for concept in chosen.concept_terms:
                concept_counts[concept] = concept_counts.get(concept, 0) + 1
            source_counts[chosen.source_key] = source_counts.get(chosen.source_key, 0) + 1
            remaining_indices.remove(best_index)

            if covered_query_terms >= query_terms and len(selected) >= min(self.limit, 3):
                break

        if len(selected) < self.limit:
            for index in remaining_indices:
                selected.append(feature_rows[index].candidate)
                if len(selected) >= self.limit:
                    break

        return selected[: self.limit]

    def _query_terms(self, question: str) -> set[str]:
        keywords = set(extract_keywords(question, limit=8))
        keywords.update(token for token in tokenize(question) if len(token) >= 2)
        return {term for term in keywords if term}

    def _expand_candidate(
        self,
        candidate: RetrievalCandidate,
        query_terms: set[str],
    ) -> SubmodularFeatures:
        chunk = candidate.chunk
        paper_title = chunk.paper_title or ""
        section_title = chunk.section_title or ""
        normalized_text = normalize_whitespace(f"{paper_title} {section_title} {chunk.chunk_text}").lower()
        query_term_hits = {term for term in query_terms if term in normalized_text}
        concept_terms = {
            term
            for term in extract_keywords(paper_title, section_title, chunk.chunk_text, limit=6)
            if term and term not in query_terms
        }
        source_key = paper_title.strip().lower() or "__unknown__"
        section_key = f"{source_key}::{section_title.strip().lower() or '__root__'}"
        return SubmodularFeatures(
            candidate=candidate,
            query_terms=query_term_hits,
            concept_terms=concept_terms,
            source_key=source_key,
            section_key=section_key,
        )

    def _global_concepts(self, feature_rows: list[SubmodularFeatures]) -> set[str]:
        concepts: list[str] = []
        for row in feature_rows:
            concepts.extend(row.concept_terms)
        ranked = []
        seen: set[str] = set()
        for concept in concepts:
            if concept in seen:
                continue
            seen.add(concept)
            ranked.append(concept)
            if len(ranked) >= 18:
                break
        return set(ranked)

    def _marginal_gain(
        self,
        features: SubmodularFeatures,
        normalized_relevance: float,
        query_terms: set[str],
        global_concepts: set[str],
        covered_query_terms: set[str],
        concept_counts: dict[str, int],
        source_counts: dict[str, int],
    ) -> float:
        query_gain = len(features.query_terms - covered_query_terms) / max(len(query_terms), 1)

        concept_gain = 0.0
        if global_concepts:
            for concept in features.concept_terms & global_concepts:
                previous = concept_counts.get(concept, 0)
                concept_gain += log1p(previous + 1) - log1p(previous)
            concept_gain /= len(global_concepts)

        source_gain = 1.0 if source_counts.get(features.source_key, 0) == 0 else 0.0

        return (
            self.relevance_weight * normalized_relevance
            + self.query_coverage_weight * query_gain
            + self.concept_coverage_weight * concept_gain
            + self.source_diversity_weight * source_gain
        )


def normalize_scores(values: list[float]) -> list[float]:
    if not values:
        return []
    maximum = max(values)
    minimum = min(values)
    if maximum == minimum:
        return [1.0 for _ in values]
    return [(value - minimum) / (maximum - minimum) for value in values]
