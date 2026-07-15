from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.bm25_retriever import Bm25Retriever
from ..selectors.topk_selector import TopKSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="bm25_topk_basic",
        retriever=Bm25Retriever(),
        selector=TopKSelector(limit=6),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
