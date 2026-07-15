from __future__ import annotations

import hashlib
from functools import lru_cache

import numpy as np
from sklearn.feature_extraction.text import HashingVectorizer

from ....core.settings import get_settings
from ....core.text_utils import normalize_whitespace


HASH_EMBEDDING_DIMENSION = 1024
HASH_EMBEDDING_ID = "local_dense_hash_v1"

_HASH_VECTORIZER = HashingVectorizer(
    n_features=HASH_EMBEDDING_DIMENSION,
    alternate_sign=False,
    analyzer="char_wb",
    ngram_range=(2, 4),
    norm=None,
)


def embed_texts_with_metadata(
    texts: list[str],
    input_type: str = "passage",
) -> tuple[str, list[list[float]]]:
    if not texts:
        return current_embedding_id(), []

    normalized_texts = [normalize_whitespace(text) for text in texts]
    encoder = _resolve_encoder()
    if encoder is None:
        return HASH_EMBEDDING_ID, _embed_with_hash(normalized_texts)
    return encoder.embedding_id, encoder.embed(normalized_texts, input_type=input_type)


def embed_texts(texts: list[str], input_type: str = "passage") -> list[list[float]]:
    _, vectors = embed_texts_with_metadata(texts, input_type=input_type)
    return vectors


def current_embedding_id() -> str:
    encoder = _resolve_encoder()
    return encoder.embedding_id if encoder is not None else HASH_EMBEDDING_ID


def stable_text_hash(text: str) -> str:
    return hashlib.sha256(normalize_whitespace(text).encode("utf-8")).hexdigest()


def _embed_with_hash(texts: list[str]) -> list[list[float]]:
    matrix = _HASH_VECTORIZER.transform(texts).astype(np.float32)
    dense = matrix.toarray()
    norms = np.linalg.norm(dense, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    normalized = dense / norms
    return normalized.tolist()


def _normalize_input_type(input_type: str) -> str:
    value = (input_type or "passage").strip().lower()
    return value if value in {"query", "passage"} else "passage"


def _prefix_texts_for_model(model_name: str, texts: list[str], input_type: str) -> list[str]:
    lower_name = model_name.lower()
    normalized_type = _normalize_input_type(input_type)
    if "e5" not in lower_name:
        return texts
    prefix = "query: " if normalized_type == "query" else "passage: "
    return [prefix + text for text in texts]


@lru_cache(maxsize=1)
def _resolve_encoder() -> "_SentenceTransformerEncoder | None":
    settings = get_settings()
    if settings.rag_embedding_backend == "hash":
        return None
    try:
        from sentence_transformers import SentenceTransformer
    except ModuleNotFoundError:
        return None

    model = SentenceTransformer(
        settings.rag_embedding_model,
        device=settings.rag_embedding_device,
    )
    return _SentenceTransformerEncoder(
        model_name=settings.rag_embedding_model,
        model=model,
    )


class _SentenceTransformerEncoder:
    def __init__(self, model_name: str, model) -> None:
        self.model_name = model_name
        self.model = model
        self.embedding_id = f"sentence_transformers:{model_name}"

    def embed(self, texts: list[str], input_type: str = "passage") -> list[list[float]]:
        prompts = _prefix_texts_for_model(self.model_name, texts, input_type)
        vectors = self.model.encode(
            prompts,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
        return vectors.astype(np.float32).tolist()
