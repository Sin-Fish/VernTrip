from __future__ import annotations

from .contracts.profile import RagProfile
from .contracts.retriever import RetrievalCandidate
from .registry import resolve_profile
from .schemas import ConversationMessage, RagChunk, RagEvidence, RagScoredChunk
from ...core.text_utils import safe_excerpt, select_relevant_sentences


def run_rag(
    question: str,
    scope: str,
    chunks: list[RagChunk],
    profile_id: str | None = None,
    conversation_context: list[ConversationMessage] | None = None,
) -> dict:
    profile = resolve_profile(profile_id)
    if not chunks:
        return {
            "answer": "当前检索范围内没有可用证据，因此暂时无法生成有依据的回答。",
            "evidences": [],
            "profile": profile_payload(profile),
        }

    ranked_candidates = retrieve_candidates(question, chunks, profile, conversation_context)
    selected_candidates = select_candidates(question, ranked_candidates, profile)
    evidences = build_evidences(selected_candidates)
    answer = build_answer(question, scope, selected_candidates, evidences, profile, conversation_context)
    return {
        "answer": answer,
        "evidences": evidences,
        "profile": profile_payload(profile),
    }


def run_retrieve(question: str, chunks: list[RagChunk], profile_id: str | None = None) -> dict:
    profile = resolve_profile(profile_id)
    if not chunks:
        return {
            "evidences": [],
            "profile": profile_payload(profile),
        }

    ranked_candidates = retrieve_candidates(question, chunks, profile)
    selected_candidates = select_candidates(question, ranked_candidates, profile)
    evidences = build_evidences(selected_candidates)
    return {
        "evidences": evidences,
        "profile": profile_payload(profile),
    }


def run_rag_from_candidates(
    question: str,
    scope: str,
    candidates: list[RagScoredChunk],
    profile_id: str | None = None,
    conversation_context: list[ConversationMessage] | None = None,
) -> dict:
    profile = resolve_profile(profile_id)
    ranked_candidates = decode_candidates(candidates)
    if not ranked_candidates:
        return {
            "answer": "当前检索范围内没有可用证据，因此暂时无法生成有依据的回答。",
            "evidences": [],
            "profile": profile_payload(profile),
        }

    selected_candidates = select_candidates(question, ranked_candidates, profile)
    evidences = build_evidences(selected_candidates)
    answer = build_answer(question, scope, selected_candidates, evidences, profile, conversation_context)
    return {
        "answer": answer,
        "evidences": evidences,
        "profile": profile_payload(profile),
    }


def run_retrieve_from_candidates(
    question: str,
    candidates: list[RagScoredChunk],
    profile_id: str | None = None,
) -> dict:
    profile = resolve_profile(profile_id)
    ranked_candidates = decode_candidates(candidates)
    if not ranked_candidates:
        return {
            "evidences": [],
            "profile": profile_payload(profile),
        }

    selected_candidates = select_candidates(question, ranked_candidates, profile)
    evidences = build_evidences(selected_candidates)
    return {
        "evidences": evidences,
        "profile": profile_payload(profile),
    }


def retrieve_candidates(
    question: str,
    chunks: list[RagChunk],
    profile: RagProfile,
    conversation_context: list[ConversationMessage] | None = None,
) -> list[RetrievalCandidate]:
    return profile.retriever.retrieve(build_retrieval_question(question, conversation_context), chunks)


def decode_candidates(candidates: list[RagScoredChunk]) -> list[RetrievalCandidate]:
    return [
        RetrievalCandidate(
            chunk=RagChunk(
                paper_id=candidate.paper_id,
                paper_title=candidate.paper_title,
                section_title=candidate.section_title,
                page_no=candidate.page_no,
                chunk_text=candidate.chunk_text,
            ),
            score=candidate.score,
        )
        for candidate in candidates
    ]


def select_candidates(
    question: str,
    candidates: list[RetrievalCandidate],
    profile: RagProfile,
) -> list[RetrievalCandidate]:
    graph_candidates = profile.graph_strategy.rerank(question, candidates)
    return profile.selector.select(question, graph_candidates)


def build_evidences(candidates: list[RetrievalCandidate]) -> list[RagEvidence]:
    evidences: list[RagEvidence] = []
    for candidate in candidates:
        if candidate.score <= 0:
            continue
        chunk = candidate.chunk
        evidences.append(
            RagEvidence(
                paper_id=chunk.paper_id,
                paper_title=chunk.paper_title,
                section_title=chunk.section_title,
                page_no=chunk.page_no,
                excerpt=safe_excerpt(chunk.chunk_text),
                score=round(float(candidate.score), 4),
            )
        )
    return evidences


def build_answer(
    question: str,
    scope: str,
    candidates: list[RetrievalCandidate],
    evidences: list[RagEvidence],
    profile: RagProfile,
    conversation_context: list[ConversationMessage] | None = None,
) -> str:
    strong_candidates = resolve_strong_candidates(candidates)
    llm_answer = profile.generator.generate(
        question,
        scope,
        evidences,
        evidence_found=bool(strong_candidates),
        conversation_context=conversation_context,
    )
    if llm_answer:
        return llm_answer

    if not strong_candidates:
        return f"在 {scope} 范围内没有检索到与问题直接相关的稳定证据，建议缩小问题范围或补充更多可读资料。"

    return build_local_answer(question, strong_candidates)


def build_local_answer(question: str, candidates: list[RetrievalCandidate]) -> str:
    strong_candidates = resolve_strong_candidates(candidates)
    if not strong_candidates:
        return "当前检索范围内没有检索到与问题直接相关的稳定证据，建议缩小问题范围或补充更多可读资料。"

    evidence_lines: list[str] = []
    synthesis_fragments: list[str] = []
    for index, candidate in enumerate(strong_candidates, start=1):
        chunk = candidate.chunk
        key_sentences = select_relevant_sentences(chunk.chunk_text, question, limit=1)
        core_sentence = key_sentences[0] if key_sentences else safe_excerpt(chunk.chunk_text, 120)
        location = f"《{chunk.paper_title}》"
        if chunk.section_title:
            location += f" 的“{chunk.section_title}”"
        if chunk.page_no:
            location += f"（第 {chunk.page_no} 页）"
        evidence_lines.append(f"{index}. {location} 提到：{core_sentence}")
        synthesis_fragments.append(core_sentence)

    synthesis = " ".join(dict.fromkeys(synthesis_fragments))
    return "基于当前检索到的证据，可以先得到以下结论：\n" + "\n".join(evidence_lines) + f"\n综合来看：{synthesis}"


def resolve_strong_candidates(candidates: list[RetrievalCandidate]) -> list[RetrievalCandidate]:
    positive_candidates = [candidate for candidate in candidates if candidate.score > 0]
    if not positive_candidates:
        return []

    top_score = positive_candidates[0].score
    if top_score < 0.03:
        return positive_candidates[:4]

    threshold = max(0.03, top_score * 0.55)
    return [candidate for candidate in positive_candidates[:6] if candidate.score >= threshold][:4]


def build_retrieval_question(
    question: str,
    conversation_context: list[ConversationMessage] | None = None,
) -> str:
    if not conversation_context:
        return question

    if not is_context_dependent_question(question):
        return question

    prior_user_questions = [
        (message.text or "").strip()
        for message in conversation_context
        if message.role == "user" and (message.text or "").strip()
    ]
    if not prior_user_questions:
        return question

    recent_questions = prior_user_questions[-2:]
    context_lines = [f"上一轮问题：{item}" for item in recent_questions]
    context_lines.append(f"当前问题：{question}")
    return "\n".join(context_lines)


def is_context_dependent_question(question: str) -> bool:
    normalized = (question or "").strip().lower()
    if not normalized:
        return False

    if len(normalized) <= 12:
        return True

    markers = (
        "它",
        "这个",
        "那个",
        "这些",
        "那些",
        "刚才",
        "上一个",
        "上一条",
        "前面",
        "上面",
        "继续",
        "进一步",
        "换一种",
        "再展开",
        "that",
        "it",
        "them",
        "those",
        "continue",
        "previous",
        "earlier",
    )
    return any(marker in normalized for marker in markers)


def profile_payload(profile: RagProfile) -> dict:
    return {
        "profile_id": profile.profile_id,
        "retriever_id": profile.retriever.retriever_id,
        "selector_id": profile.selector.selector_id,
        "graph_strategy_id": profile.graph_strategy.graph_strategy_id,
        "generator_id": profile.generator.generator_id,
    }
