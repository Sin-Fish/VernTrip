from __future__ import annotations

from ..contracts.profile import RagProfile
from ..generators.deepseek_generator import DeepSeekGenerator
from ..graph_strategies.random_walk_rerank_strategy import RandomWalkRerankStrategy
from ..retrievers.backend_dense_retriever import BackendDenseRetriever
from ..selectors.topk_selector import TopKSelector


def build_profile() -> RagProfile:
    return RagProfile(
        profile_id="hybrid_pgvector_random_walk",
        retriever=BackendDenseRetriever(),
        selector=TopKSelector(limit=6),
        graph_strategy=RandomWalkRerankStrategy(),
        generator=DeepSeekGenerator(),
    )
