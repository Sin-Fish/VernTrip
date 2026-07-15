from __future__ import annotations

from .contracts.profile import RagProfile
from .profiles.bm25_mmr_basic import build_profile as build_bm25_mmr_basic
from .profiles.bm25_coverage_basic import build_profile as build_bm25_coverage_basic
from .profiles.bm25_submodular_basic import build_profile as build_bm25_submodular_basic
from .profiles.bm25_topk_basic import build_profile as build_bm25_topk_basic
from .profiles.dense_pgvector_coverage_basic import build_profile as build_dense_pgvector_coverage_basic
from .profiles.dense_pgvector_mmr_basic import build_profile as build_dense_pgvector_mmr_basic
from .profiles.dense_pgvector_submodular_basic import build_profile as build_dense_pgvector_submodular_basic
from .profiles.dense_pgvector_submodular_random_walk import build_profile as build_dense_pgvector_submodular_random_walk
from .profiles.dense_pgvector_topk_basic import build_profile as build_dense_pgvector_topk_basic
from .profiles.hybrid_pgvector_coverage import build_profile as build_hybrid_pgvector_coverage
from .profiles.hybrid_pgvector_mmr import build_profile as build_hybrid_pgvector_mmr
from .profiles.hybrid_pgvector_random_walk import build_profile as build_hybrid_pgvector_random_walk
from .profiles.hybrid_pgvector_submodular import build_profile as build_hybrid_pgvector_submodular
from .profiles.hybrid_pgvector_submodular_random_walk import build_profile as build_hybrid_pgvector_submodular_random_walk
from .profiles.hybrid_pgvector_topk_basic import build_profile as build_hybrid_pgvector_topk_basic
from .profiles.hybrid_coverage import build_profile as build_hybrid_coverage
from .profiles.hybrid_mmr import build_profile as build_hybrid_mmr
from .profiles.hybrid_random_walk import build_profile as build_hybrid_random_walk
from .profiles.hybrid_rrf_basic import build_profile as build_hybrid_rrf_basic
from .profiles.hybrid_submodular import build_profile as build_hybrid_submodular
from .profiles.hybrid_submodular_random_walk import build_profile as build_hybrid_submodular_random_walk
from .profiles.tfidf_coverage_basic import build_profile as build_tfidf_coverage_basic
from .profiles.tfidf_mmr_basic import build_profile as build_tfidf_mmr_basic
from .profiles.tfidf_submodular_basic import build_profile as build_tfidf_submodular_basic
from .profiles.tfidf_topk_basic import build_profile as build_tfidf_topk_basic


DEFAULT_PROFILE_ID = "tfidf_topk_basic"

_PROFILES: dict[str, RagProfile] = {
    DEFAULT_PROFILE_ID: build_tfidf_topk_basic(),
    "tfidf_mmr_basic": build_tfidf_mmr_basic(),
    "tfidf_coverage_basic": build_tfidf_coverage_basic(),
    "tfidf_submodular_basic": build_tfidf_submodular_basic(),
    "bm25_topk_basic": build_bm25_topk_basic(),
    "bm25_mmr_basic": build_bm25_mmr_basic(),
    "bm25_coverage_basic": build_bm25_coverage_basic(),
    "bm25_submodular_basic": build_bm25_submodular_basic(),
    "dense_pgvector_topk_basic": build_dense_pgvector_topk_basic(),
    "dense_pgvector_mmr_basic": build_dense_pgvector_mmr_basic(),
    "dense_pgvector_coverage_basic": build_dense_pgvector_coverage_basic(),
    "dense_pgvector_submodular_basic": build_dense_pgvector_submodular_basic(),
    "dense_pgvector_submodular_random_walk": build_dense_pgvector_submodular_random_walk(),
    "hybrid_pgvector_topk_basic": build_hybrid_pgvector_topk_basic(),
    "hybrid_pgvector_mmr": build_hybrid_pgvector_mmr(),
    "hybrid_pgvector_coverage": build_hybrid_pgvector_coverage(),
    "hybrid_pgvector_submodular": build_hybrid_pgvector_submodular(),
    "hybrid_pgvector_random_walk": build_hybrid_pgvector_random_walk(),
    "hybrid_pgvector_submodular_random_walk": build_hybrid_pgvector_submodular_random_walk(),
    "hybrid_rrf_basic": build_hybrid_rrf_basic(),
    "hybrid_mmr": build_hybrid_mmr(),
    "hybrid_coverage": build_hybrid_coverage(),
    "hybrid_submodular": build_hybrid_submodular(),
    "hybrid_random_walk": build_hybrid_random_walk(),
    "hybrid_submodular_random_walk": build_hybrid_submodular_random_walk(),
}


def resolve_profile(profile_id: str | None) -> RagProfile:
    normalized = (profile_id or DEFAULT_PROFILE_ID).strip() or DEFAULT_PROFILE_ID
    return _PROFILES.get(normalized, _PROFILES[DEFAULT_PROFILE_ID])
