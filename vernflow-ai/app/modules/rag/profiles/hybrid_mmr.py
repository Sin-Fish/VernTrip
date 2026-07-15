from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.hybrid_rrf_retriever import HybridRrfRetriever
from ..selectors.mmr_selector import MmrSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="hybrid_mmr",
        retriever=HybridRrfRetriever(),
        selector=MmrSelector(limit=6, candidate_pool=18, lambda_weight=0.7),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
