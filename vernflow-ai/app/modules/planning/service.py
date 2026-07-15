from __future__ import annotations

import json

from ...core.deepseek_client import DeepSeekClient, DeepSeekError
from ...core.text_utils import normalize_whitespace
from .schemas import WorkbenchPlanRequest


NETWORK_HINTS = ("网页", "网络", "外部", "搜索", "补充", "最新", "竞品", "行业")
INSPECT_HINTS = ("检查", "核对", "审查", "引用", "证据", "断言", "准确")
WRITE_HINTS = ("写", "生成", "报告", "总结", "方案", "文档", "稿", "输出")


def build_workbench_plan(request: WorkbenchPlanRequest) -> dict:
    llm_payload = build_plan_with_deepseek(request)
    if llm_payload is not None:
        return llm_payload
    return build_local_plan(request)


def build_plan_with_deepseek(request: WorkbenchPlanRequest) -> dict | None:
    client = DeepSeekClient()
    if not client.is_enabled():
        return None

    tools_text = "\n".join(
        f"- {tool.id}: {tool.name} | type={tool.type} | branch={tool.branchTool} | "
        f"read={tool.readWorkspace} | write={tool.writeWorkspace} | network={tool.useNetwork} | {tool.summary}"
        for tool in request.availableTools
    )
    documents_text = "\n\n".join(
        f"[{index}] {document.citeLabel} | {document.sectionTitle} | chunks={document.chunkCount}\n"
        f"{document.excerpt}"
        for index, document in enumerate(request.documents[:8], start=1)
    ) or "无可读文档摘要。"

    messages = [
        {
            "role": "system",
            "content": (
                "你是 VernFlow 的工作台决策规划器。"
                "你的职责只有一件事：根据任务目标、当前范围和可用工具，为工作台生成结构化工具计划。"
                "你不能执行工具，只能规划。"
                "请严格输出 JSON 对象，只允许包含以下字段："
                "title, summary, inspectMode, mainTools, branchTools, nodeNotes。"
                "mainTools 只能从可用工具里选择，顺序必须符合实际执行顺序。"
                "branchTools 只放离主路径的补充分支。"
                "inspectMode 只能是 focus 或 full。"
                "nodeNotes 是对象数组，每项包含 toolId, title, detail, rationale。"
                "不要输出 Markdown，不要输出额外解释。"
            ),
        },
        {
            "role": "user",
            "content": (
                f"任务：{request.prompt}\n"
                f"范围模式：{request.scopeMode}\n"
                f"工作文件数：{request.workingFileCount}\n"
                f"范围文件总数：{request.totalScopeFileCount}\n"
                f"可读文档数：{request.readableDocumentCount}\n"
                f"工作文件标签：{', '.join(request.workingFileLabels) or '无'}\n"
                f"不可读文件：{', '.join(request.unreadableFiles) or '无'}\n\n"
                f"可用工具：\n{tools_text}\n\n"
                f"文档摘要：\n{documents_text}\n\n"
                "请给出适合当前任务的工作台工具计划。"
            ),
        },
    ]

    try:
        content = client.chat(messages, temperature=0.2, max_tokens=1600)
        payload = extract_json_object(content)
    except (DeepSeekError, ValueError, json.JSONDecodeError):
        return None

    if not isinstance(payload, dict):
        return None

    return normalize_llm_payload(payload, request)


def normalize_llm_payload(payload: dict, request: WorkbenchPlanRequest) -> dict:
    tool_map = {tool.id: tool for tool in request.availableTools}
    canonical_main = ["scope-read", "local-retrieval", "material-organize", "artifact-write", "claim-inspection"]
    required_main = {"scope-read", "local-retrieval", "claim-inspection"}

    main_tools = [
        tool_id for tool_id in payload.get("mainTools", [])
        if isinstance(tool_id, str)
        and tool_id in tool_map
        and not tool_map[tool_id].branchTool
    ]
    branch_tools = [
        tool_id for tool_id in payload.get("branchTools", [])
        if isinstance(tool_id, str)
        and tool_id in tool_map
        and tool_map[tool_id].branchTool
    ]

    normalized_main = []
    for tool_id in canonical_main:
        if tool_id in main_tools or tool_id in required_main:
            normalized_main.append(tool_id)

    prompt = request.prompt or ""
    if should_prefer_writer(prompt) and "artifact-write" not in normalized_main:
        insert_after(normalized_main, "material-organize", "artifact-write")
    if "material-organize" not in normalized_main:
        insert_after(normalized_main, "local-retrieval", "material-organize")

    branch_tools = list(dict.fromkeys(branch_tools))
    inspect_mode = payload.get("inspectMode")
    if inspect_mode not in {"focus", "full"}:
        inspect_mode = "full" if contains_any(prompt, INSPECT_HINTS) else "focus"

    node_notes = []
    raw_notes = payload.get("nodeNotes", [])
    if isinstance(raw_notes, list):
        for note in raw_notes:
            if not isinstance(note, dict):
                continue
            tool_id = note.get("toolId")
            if tool_id not in tool_map:
                continue
            if tool_id not in normalized_main and tool_id not in branch_tools:
                continue
            node_notes.append(
                {
                    "toolId": tool_id,
                    "title": abbreviate(str(note.get("title") or "").strip(), 36) or default_title(tool_id),
                    "detail": abbreviate(str(note.get("detail") or "").strip(), 48) or default_detail(tool_id, request),
                    "rationale": abbreviate(str(note.get("rationale") or "").strip(), 88) or default_rationale(tool_id),
                }
            )

    return {
        "title": abbreviate(str(payload.get("title") or "").strip(), 42) or default_plan_title(request),
        "summary": abbreviate(str(payload.get("summary") or "").strip(), 120) or default_summary(request, normalized_main, branch_tools),
        "inspectMode": inspect_mode,
        "mainTools": normalized_main,
        "branchTools": branch_tools,
        "nodeNotes": node_notes,
    }


def build_local_plan(request: WorkbenchPlanRequest) -> dict:
    prompt = request.prompt or ""
    include_writer = should_prefer_writer(prompt)
    include_web = contains_any(prompt, NETWORK_HINTS)
    inspect_mode = "full" if contains_any(prompt, INSPECT_HINTS) else "focus"

    main_tools = ["scope-read", "local-retrieval", "material-organize"]
    if include_writer:
        main_tools.append("artifact-write")
    main_tools.append("claim-inspection")
    branch_tools = ["web-supplement"] if include_web else []

    return {
        "title": default_plan_title(request),
        "summary": default_summary(request, main_tools, branch_tools),
        "inspectMode": inspect_mode,
        "mainTools": main_tools,
        "branchTools": branch_tools,
        "nodeNotes": [
            {
                "toolId": "scope-read",
                "title": "读取当前范围",
                "detail": "锁定本次任务的输入边界",
                "rationale": "先确认范围，再安排后续节点。",
            },
            {
                "toolId": "local-retrieval",
                "title": "本地检索",
                "detail": "优先召回高相关本地证据",
                "rationale": "后续整理和检查都建立在证据召回之上。",
            },
            {
                "toolId": "material-organize",
                "title": "整理材料",
                "detail": "提炼结构、要点与引用线索",
                "rationale": "先把证据整理成结构，再决定输出形态。",
            },
            *([
                {
                    "toolId": "artifact-write",
                    "title": "生成结果草稿",
                    "detail": "写出工作区结果草稿",
                    "rationale": "给后续检查一个明确的输出对象。",
                }
            ] if include_writer else []),
            {
                "toolId": "claim-inspection",
                "title": "结果检查",
                "detail": "检查覆盖率、断言和引用绑定",
                "rationale": "最后确认结果没有脱离证据。",
            },
            *([
                {
                    "toolId": "web-supplement",
                    "title": "网页补充",
                    "detail": "本地证据不足时补充公开资料",
                    "rationale": "作为分支使用，避免一开始就引入外部噪声。",
                }
            ] if include_web else []),
        ],
    }


def extract_json_object(content: str) -> dict:
    normalized = content.strip()
    if normalized.startswith("```"):
        normalized = normalized.strip("`")
        if normalized.startswith("json"):
            normalized = normalized[4:].strip()

    start = normalized.find("{")
    end = normalized.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Planner response does not contain a JSON object.")

    return json.loads(normalized[start:end + 1])


def default_plan_title(request: WorkbenchPlanRequest) -> str:
    if request.prompt.strip():
        return abbreviate(normalize_whitespace(request.prompt), 42)
    if request.workingFileLabels:
        return abbreviate(f"资料任务 - {request.workingFileLabels[0]}", 42)
    return "资料任务规划"


def default_summary(request: WorkbenchPlanRequest, main_tools: list[str], branch_tools: list[str]) -> str:
    scope_text = "全局工作区" if request.scopeMode == "global_workspace" else "工作文件"
    write_text = "，生成结果草稿" if "artifact-write" in main_tools else ""
    branch_text = "，必要时走外部分支补充" if branch_tools else ""
    return f"先读取{scope_text}并做本地检索，整理关键材料{write_text}{branch_text}，最后执行结果检查。"


def default_title(tool_id: str) -> str:
    return {
        "scope-read": "读取当前范围",
        "local-retrieval": "本地检索",
        "web-supplement": "网页补充",
        "material-organize": "整理材料",
        "artifact-write": "生成结果草稿",
        "claim-inspection": "结果检查",
    }.get(tool_id, tool_id)


def default_detail(tool_id: str, request: WorkbenchPlanRequest) -> str:
    if tool_id == "scope-read":
        return "锁定当前任务范围"
    if tool_id == "local-retrieval":
        return f"可读资料 {request.readableDocumentCount} 份"
    if tool_id == "web-supplement":
        return "公开资料补充分支"
    if tool_id == "material-organize":
        return "结构化整理"
    if tool_id == "artifact-write":
        return "工作区结果草稿"
    if tool_id == "claim-inspection":
        return "覆盖率与引用检查"
    return "待规划"


def default_rationale(tool_id: str) -> str:
    return {
        "scope-read": "先确认输入边界，再安排后续节点。",
        "local-retrieval": "后续整理和检查都依赖证据召回结果。",
        "web-supplement": "本地证据不足时再扩大信息源。",
        "material-organize": "先把证据整理成结构，避免直接输出松散内容。",
        "artifact-write": "先产出结果草稿，后续检查才有明确对象。",
        "claim-inspection": "最后确认覆盖率、引用绑定和断言稳健性。",
    }.get(tool_id, "保留当前节点。")


def insert_after(items: list[str], anchor: str, value: str) -> None:
    if value in items:
        return
    if anchor in items:
        items.insert(items.index(anchor) + 1, value)
    else:
        items.append(value)


def should_prefer_writer(prompt: str) -> bool:
    return contains_any(prompt, WRITE_HINTS) or not contains_any(prompt, INSPECT_HINTS)


def contains_any(text: str, hints: tuple[str, ...]) -> bool:
    normalized = (text or "").lower()
    return any(hint in normalized for hint in hints)


def abbreviate(value: str, limit: int) -> str:
    normalized = normalize_whitespace(value) or ""
    if len(normalized) <= limit:
        return normalized
    return normalized[: max(0, limit - 3)].strip() + "..."
