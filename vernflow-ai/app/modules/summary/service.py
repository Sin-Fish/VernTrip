from __future__ import annotations

import json

from ...core.deepseek_client import DeepSeekClient, DeepSeekError
from ...core.text_utils import extract_keywords, normalize_whitespace, split_sentences


def build_summary(title: str | None, abstract_text: str | None, introduction_text: str | None) -> dict[str, str | list[str]]:
    keywords = extract_keywords(title, abstract_text, introduction_text)
    llm_summary = build_summary_with_deepseek(title, abstract_text, introduction_text, keywords)
    if llm_summary is not None:
        return llm_summary

    return build_local_summary(title, abstract_text, introduction_text, keywords)


def build_summary_with_deepseek(
    title: str | None,
    abstract_text: str | None,
    introduction_text: str | None,
    keywords: list[str],
) -> dict[str, str | list[str]] | None:
    source_text = normalize_whitespace(abstract_text) or normalize_whitespace(introduction_text)
    if not source_text:
        return None

    client = DeepSeekClient()
    if not client.is_enabled():
        return None

    title_text = title or "未命名文档"
    messages = [
        {
            "role": "system",
            "content": (
                "你是 VernFlow 的文献阅读助手。"
                "请根据给定内容输出一个 JSON 对象，只包含以下字段："
                "ai_summary, summary_problem, summary_method, summary_conclusion, summary_value。"
                "每个字段都用简洁中文表述，不要输出 Markdown，不要输出额外说明。"
            ),
        },
        {
            "role": "user",
            "content": (
                f"标题：{title_text}\n"
                f"摘要/引言：{source_text[:6000]}\n"
                "请严格输出 JSON。"
            ),
        },
    ]

    try:
        content = client.chat(messages, temperature=0.2, max_tokens=1100)
        payload = extract_json_object(content)
    except (DeepSeekError, ValueError, json.JSONDecodeError):
        return None

    required_fields = [
        "ai_summary",
        "summary_problem",
        "summary_method",
        "summary_conclusion",
        "summary_value",
    ]
    if not all(isinstance(payload.get(field), str) and payload.get(field).strip() for field in required_fields):
        return None

    return {
        "ai_summary": payload["ai_summary"].strip(),
        "summary_problem": payload["summary_problem"].strip(),
        "summary_method": payload["summary_method"].strip(),
        "summary_conclusion": payload["summary_conclusion"].strip(),
        "summary_value": payload["summary_value"].strip(),
        "keywords": keywords,
    }


def build_local_summary(
    title: str | None,
    abstract_text: str | None,
    introduction_text: str | None,
    keywords: list[str],
) -> dict[str, str | list[str]]:
    summary_source = normalize_whitespace(abstract_text) or normalize_whitespace(introduction_text)
    sentences = split_sentences(summary_source)

    overview_sentence = sentences[0] if sentences else f"当前文档《{title or '未命名文档'}》已完成基础解析，但尚未提炼出足够完整的正文摘要。"
    method_sentence = _pick_sentence(
        sentences,
        ["propose", "method", "approach", "framework", "model", "algorithm", "提出", "方法", "框架", "模型", "算法"],
        fallback_index=1,
    )
    conclusion_sentence = _pick_sentence(
        sentences,
        ["result", "conclusion", "show", "demonstrate", "achieve", "结果", "结论", "表明", "实验", "性能"],
        fallback_index=-1,
    )
    problem_sentence = _pick_sentence(
        sentences,
        ["problem", "challenge", "task", "address", "question", "问题", "挑战", "目标", "任务"],
        fallback_index=0,
    )

    summary_problem = _wrap_reading_note("研究问题", problem_sentence or overview_sentence)
    summary_method = _wrap_reading_note("核心方法", method_sentence or overview_sentence)
    summary_conclusion = _wrap_reading_note("主要结论", conclusion_sentence or overview_sentence)
    summary_value = _build_reading_value(title, overview_sentence, method_sentence)

    ai_summary = "；".join([
        _wrap_reading_note("一句话概括", overview_sentence),
        summary_problem,
        summary_method,
        summary_conclusion,
        summary_value,
    ])

    return {
        "ai_summary": ai_summary,
        "summary_problem": summary_problem,
        "summary_method": summary_method,
        "summary_conclusion": summary_conclusion,
        "summary_value": summary_value,
        "keywords": keywords,
    }


def extract_json_object(content: str) -> dict[str, str]:
    normalized = content.strip()
    if normalized.startswith("```"):
        normalized = normalized.strip("`")
        if normalized.startswith("json"):
            normalized = normalized[4:].strip()

    start = normalized.find("{")
    end = normalized.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("DeepSeek response does not contain a JSON object.")

    return json.loads(normalized[start:end + 1])


def _pick_sentence(sentences: list[str], keywords: list[str], fallback_index: int) -> str:
    lowered_keywords = [keyword.lower() for keyword in keywords]
    for sentence in sentences:
        lowered_sentence = sentence.lower()
        if any(keyword in lowered_sentence for keyword in lowered_keywords):
            return sentence
    if not sentences:
        return ""
    if fallback_index < 0:
        return sentences[fallback_index]
    return sentences[min(fallback_index, len(sentences) - 1)]


def _wrap_reading_note(label: str, content: str) -> str:
    normalized_content = normalize_whitespace(content)
    if not normalized_content:
        return f"{label}：当前文本不足，暂时无法稳定提炼。"
    return f"{label}：{normalized_content}"


def _build_reading_value(title: str | None, overview_sentence: str, method_sentence: str) -> str:
    key_phrase = normalize_whitespace(method_sentence or overview_sentence)
    if not key_phrase:
        return "阅读价值：适合用来建立该方向的基础问题背景。"
    if title:
        return f"阅读价值：适合把《{title}》作为该主题的快速切入口，并结合原文进一步核对细节。"
    return f"阅读价值：适合围绕“{key_phrase[:36]}”建立后续精读和对比阅读清单。"
