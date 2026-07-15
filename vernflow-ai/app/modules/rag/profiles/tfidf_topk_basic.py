from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.noop_graph_strategy import NoOpGraphStrategy
from ..retrievers.tfidf_retriever import TfidfRetriever
from ..selectors.topk_selector import TopKSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="tfidf_topk_basic",
        retriever=TfidfRetriever(),
        selector=TopKSelector(limit=6),
        graph_strategy=NoOpGraphStrategy(),
        generator=DeepSeekGenerator(),
    )
