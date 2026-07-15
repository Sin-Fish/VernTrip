from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.random_walk_rerank_strategy import RandomWalkRerankStrategy
from ..retrievers.hybrid_rrf_retriever import HybridRrfRetriever
from ..selectors.submodular_coverage_selector import SubmodularCoverageSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="hybrid_submodular_random_walk",
        retriever=HybridRrfRetriever(),
        selector=SubmodularCoverageSelector(limit=6, candidate_pool=20),
        graph_strategy=RandomWalkRerankStrategy(),
        generator=DeepSeekGenerator(),
    )
