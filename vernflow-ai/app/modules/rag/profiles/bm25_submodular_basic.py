from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.bm25_retriever import Bm25Retriever
from ..selectors.submodular_coverage_selector import SubmodularCoverageSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="bm25_submodular_basic",
        retriever=Bm25Retriever(),
        selector=SubmodularCoverageSelector(limit=6, candidate_pool=20),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
