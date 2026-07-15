from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.hybrid_rrf_retriever import HybridRrfRetriever
from ..selectors.coverage_selector import CoverageSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="hybrid_coverage",
        retriever=HybridRrfRetriever(),
        selector=CoverageSelector(limit=6, candidate_pool=18),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
