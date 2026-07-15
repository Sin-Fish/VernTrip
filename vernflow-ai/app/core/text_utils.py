from __future__ import annotations

import re
from collections import Counter


ENGLISH_STOP_WORDS = {
    "the", "and", "for", "that", "with", "from", "this", "these", "those", "into", "their", "paper",
    "study", "using", "based", "model", "models", "method", "methods", "results", "result", "approach",
    "analysis", "system", "systems", "proposed", "propose", "research", "learning", "deep", "data",
}

CHINESE_STOP_WORDS = {"我们", "本文", "一种", "研究", "通过", "进行", "以及", "对于", "相关", "方法", "模型", "结果"}


def normalize_whitespace(text: str | None) -> str:
    if not text:
        return ""
    return re.sub(r"\s+", " ", text).strip()


def split_sentences(text: str | None) -> list[str]:
    normalized_text = normalize_whitespace(text)
    if not normalized_text:
        return []
    fragments = re.split(r"(?<=[。！？!?\.])\s+|(?<=[。！？!?])", normalized_text)
    return [fragment.strip() for fragment in fragments if fragment and fragment.strip()]


def safe_excerpt(text: str, limit: int = 260) -> str:
    normalized_text = normalize_whitespace(text)
    if len(normalized_text) <= limit:
        return normalized_text
    return normalized_text[: limit - 1].rstrip() + "…"


def extract_keywords(*texts: str | None, limit: int = 8) -> list[str]:
    joined_text = normalize_whitespace(" ".join(text for text in texts if text))
    if not joined_text:
        return []

    token_counter: Counter[str] = Counter()

    for token in re.findall(r"[A-Za-z][A-Za-z\-]{2,}|[\u4e00-\u9fff]{2,}", joined_text):
        normalized_token = token.lower()
        if normalized_token in ENGLISH_STOP_WORDS or normalized_token in CHINESE_STOP_WORDS:
            continue
        token_counter[normalized_token] += 1

    keywords: list[str] = []
    for token, _ in token_counter.most_common(limit * 2):
        keywords.append(token)
        if len(keywords) >= limit:
            break
    return keywords


def sentence_relevance_score(sentence: str, question: str) -> float:
    if not sentence:
        return 0.0
    sentence_terms = set(re.findall(r"[A-Za-z]{2,}|[\u4e00-\u9fff]", sentence.lower()))
    question_terms = set(re.findall(r"[A-Za-z]{2,}|[\u4e00-\u9fff]", question.lower()))
    if not sentence_terms or not question_terms:
        return 0.0
    overlap_count = len(sentence_terms & question_terms)
    return overlap_count / len(question_terms)


def select_relevant_sentences(text: str, question: str, limit: int = 2) -> list[str]:
    sentences = split_sentences(text)
    if not sentences:
        return []
    ranked_sentences = sorted(
        sentences,
        key=lambda sentence: (sentence_relevance_score(sentence, question), len(sentence)),
        reverse=True,
    )
    selected_sentences: list[str] = []
    for sentence in ranked_sentences:
        if sentence not in selected_sentences:
            selected_sentences.append(sentence)
        if len(selected_sentences) >= limit:
            break
    return selected_sentences
