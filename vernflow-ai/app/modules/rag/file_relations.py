from __future__ import annotations

import math
import re
from collections import OrderedDict

from ...core.text_utils import normalize_whitespace
from .embeddings.local_dense_encoder import embed_texts
from .schemas import (
    FileRelationDocument,
    FileRelationEdge,
    FileRelationGraphResponse,
    FileRelationNode,
)


CONCEPT_PATTERN = re.compile(r"[A-Za-z][A-Za-z0-9_-]{2,}|[\u4e00-\u9fff]{2,8}")
ENGLISH_STOP_WORDS = {
    "this", "that", "with", "from", "into", "about", "there", "their", "while", "where",
    "which", "what", "when", "your", "have", "has", "were", "been", "will", "would",
    "shall", "could", "should", "than", "then", "them", "they", "through", "such",
    "using", "used", "also", "over", "under", "more", "most", "very", "just",
    "does", "did", "each", "much", "many", "after", "before", "between",
}


def run_file_relations(
    focus_path: str,
    documents: list[FileRelationDocument],
    limit: int,
) -> dict:
    if not focus_path:
        return FileRelationGraphResponse(
            focus_path=None,
            focus_label=None,
            status_message="请先选择一个文件，再查看关联图。",
            nodes=[],
            edges=[],
        ).model_dump()

    if not documents:
        return FileRelationGraphResponse(
            focus_path=focus_path,
            focus_label=_file_label_from_path(focus_path),
            status_message="当前工作区里还没有可分析的文件。",
            nodes=[],
            edges=[],
        ).model_dump()

    features = _build_document_features(documents)
    focus = next((feature for feature in features if feature["path"] == focus_path), None)
    if focus is None:
        return FileRelationGraphResponse(
            focus_path=focus_path,
            focus_label=_file_label_from_path(focus_path),
            status_message="当前文件暂时还没有可展示的关联关系。",
            nodes=[],
            edges=[],
        ).model_dump()

    relations = []
    for candidate in features:
        if candidate["path"] == focus["path"]:
            continue
        relation = _build_relation(focus, candidate)
        if relation["usable"]:
            relations.append(relation)

    relations.sort(key=lambda item: item["score"], reverse=True)
    relations = relations[: max(1, limit)]

    nodes = [
        FileRelationNode(
            id=focus["path"],
            path=focus["path"],
            label=focus["label"],
            directory_label=focus["directory_label"],
            role="focus",
            score=1.0,
            concepts=focus["concepts"],
        )
    ]
    edges = []
    for relation in relations:
        target = relation["target"]
        nodes.append(
            FileRelationNode(
                id=target["path"],
                path=target["path"],
                label=target["label"],
                directory_label=target["directory_label"],
                role="related",
                score=relation["score"],
                concepts=target["concepts"],
            )
        )
        edges.append(
            FileRelationEdge(
                id=f'{focus["path"]}->{target["path"]}',
                source=focus["path"],
                target=target["path"],
                relation_type=relation["relation_type"],
                score=relation["score"],
                reasons=relation["reasons"],
            )
        )

    message = (
        "当前文件暂时没有明显关联，已展示该文件本身。"
        if not relations
        else "已展示当前文件最相关的文件关系。"
    )
    return FileRelationGraphResponse(
        focus_path=focus["path"],
        focus_label=focus["label"],
        status_message=message,
        nodes=nodes,
        edges=edges,
    ).model_dump()


def _build_document_features(documents: list[FileRelationDocument]) -> list[dict]:
    snippets = [normalize_whitespace(document.snippet) for document in documents]
    vectors = embed_texts(snippets, input_type="passage") if snippets else []
    features = []
    for index, document in enumerate(documents):
        features.append(
            {
                "path": document.path,
                "label": document.label or _file_label_from_path(document.path),
                "directory_label": document.directory_label or "workspace root",
                "concepts": _extract_concepts(document.snippet),
                "embedding": vectors[index] if index < len(vectors) else None,
            }
        )
    return features


def _extract_concepts(text: str) -> list[str]:
    if not text:
        return []

    counts: OrderedDict[str, int] = OrderedDict()
    for match in CONCEPT_PATTERN.finditer(text):
        normalized = _normalize_concept(match.group())
        if not normalized or normalized in ENGLISH_STOP_WORDS:
            continue
        counts[normalized] = counts.get(normalized, 0) + 1

    ranked = sorted(counts.items(), key=lambda item: item[1], reverse=True)
    return [token for token, _ in ranked[:8]]


def _normalize_concept(raw: str) -> str | None:
    if not raw:
        return None

    token = raw.strip()
    if any("\u4e00" <= char <= "\u9fff" for char in token):
        return token

    token = token.lower()
    return token if len(token) >= 3 else None


def _build_relation(source: dict, target: dict) -> dict:
    shared = _shared_concepts(source["concepts"], target["concepts"])
    lexical_score = _lexical_overlap(source["concepts"], target["concepts"])
    semantic_score = _cosine(source["embedding"], target["embedding"])
    same_directory_bonus = 0.08 if source["directory_label"] == target["directory_label"] else 0.0

    score = (
        (semantic_score * 0.7) + (lexical_score * 0.25) + same_directory_bonus
        if semantic_score > 0
        else lexical_score + same_directory_bonus
    )
    score = min(1.0, score)

    reasons = []
    if shared:
        reasons.append("共享关键词：" + "、".join(shared[:4]))
    if semantic_score > 0:
        reasons.append(f"语义相似度：{semantic_score:.2f}")
    if same_directory_bonus > 0:
        reasons.append(f'位于同一目录：{source["directory_label"]}')

    if semantic_score >= 0.7 and len(shared) >= 2:
        relation_type = "semantic_similar"
    elif len(shared) >= 2:
        relation_type = "shared_concepts"
    else:
        relation_type = "related"

    return {
        "target": target,
        "relation_type": relation_type,
        "score": score,
        "reasons": reasons,
        "usable": score >= 0.18 or bool(reasons),
    }


def _shared_concepts(left: list[str], right: list[str]) -> list[str]:
    if not left or not right:
        return []
    right_lookup = set(right)
    return [token for token in left if token in right_lookup][:5]


def _lexical_overlap(left: list[str], right: list[str]) -> float:
    if not left or not right:
        return 0.0
    left_set = set(left)
    right_set = set(right)
    intersection = len(left_set & right_set)
    if intersection == 0:
        return 0.0
    return intersection / max(len(left_set), len(right_set))


def _cosine(left: list[float] | None, right: list[float] | None) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0

    dot = sum(left_value * right_value for left_value, right_value in zip(left, right))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if left_norm <= 0 or right_norm <= 0:
        return 0.0
    return max(0.0, dot / (left_norm * right_norm))


def _file_label_from_path(path: str | None) -> str:
    if not path:
        return "未命名文件"
    normalized = path[:-1] if path.endswith("/") else path
    if "/" not in normalized:
        return normalized
    return normalized.rsplit("/", 1)[-1]
