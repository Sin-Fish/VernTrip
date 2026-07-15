from __future__ import annotations

from ....core.deepseek_client import DeepSeekClient, DeepSeekError
from ..schemas import ConversationMessage, RagEvidence


class DeepSeekGenerator:
    generator_id = "deepseek_chat"

    def generate(
        self,
        question: str,
        scope: str,
        evidences: list[RagEvidence],
        *,
        evidence_found: bool,
        conversation_context: list[ConversationMessage] | None = None,
    ) -> str | None:
        client = DeepSeekClient()
        if not client.is_enabled():
            return None

        context_text = format_conversation_context(conversation_context or [])

        if evidence_found and evidences:
            evidence_blocks: list[str] = []
            for index, evidence in enumerate(evidences, start=1):
                location = evidence.paper_title
                if evidence.section_title:
                    location += f" / {evidence.section_title}"
                if evidence.page_no:
                    location += f" / 第 {evidence.page_no} 页"
                evidence_blocks.append(
                    f"[{index}] {location}\n"
                    f"score: {evidence.score}\n"
                    f"excerpt: {evidence.excerpt}"
                )
            evidence_text = "\n\n".join(evidence_blocks)

            messages = [
                {
                    "role": "system",
                    "content": (
                        "你是 VernFlow 的文档助手。"
                        "你只能依据用户给出的证据回答，不要编造证据中没有的信息。"
                        "如果证据不足，请明确指出不足之处，并建议用户缩小范围或补充资料。"
                        "请用简洁中文直接回答。"
                    ),
                },
                {
                    "role": "user",
                    "content": (
                        (f"对话上下文：\n{context_text}\n\n" if context_text else "")
                        + f"问题：{question}\n"
                        + f"范围：{scope}\n\n"
                        + f"证据：\n{evidence_text}\n\n"
                        + "请给出一段综合回答。"
                    ),
                },
            ]
        else:
            messages = [
                {
                    "role": "system",
                    "content": (
                        "你是 VernFlow 的文档助手。"
                        "当前没有检索到能直接回答问题的稳定证据。"
                        "请不要编造文档内容。"
                        "如果用户在打招呼或泛化闲聊，可以礼貌回应，并提醒这是一个基于资料范围的助手。"
                        "如果用户在问资料问题，请说明当前范围证据不足，并建议补充工作文件或缩小问题。"
                        "请用简洁中文回答。"
                    ),
                },
                {
                    "role": "user",
                    "content": (
                        (f"对话上下文：\n{context_text}\n\n" if context_text else "")
                        + f"问题：{question}\n范围：{scope}\n当前没有可直接引用的稳定证据。"
                    ),
                },
            ]

        try:
            return client.chat(messages, temperature=0.2, max_tokens=900)
        except DeepSeekError:
            return None


def format_conversation_context(messages: list[ConversationMessage]) -> str:
    lines: list[str] = []
    for message in messages:
        text = (message.text or "").strip()
        if not text:
            continue
        role_label = "用户" if message.role == "user" else "助手"
        lines.append(f"{role_label}: {text}")
    return "\n".join(lines[-6:])
