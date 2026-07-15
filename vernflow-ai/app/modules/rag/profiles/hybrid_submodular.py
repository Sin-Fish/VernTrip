from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.hybrid_rrf_retriever import HybridRrfRetriever
from ..selectors.submodular_coverage_selector import SubmodularCoverageSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="hybrid_submodular",
        retriever=HybridRrfRetriever(),
        selector=SubmodularCoverageSelector(limit=6, candidate_pool=20),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
