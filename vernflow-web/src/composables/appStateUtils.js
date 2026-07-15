export function ensureArray(value, fallback) {
  return Array.isArray(value) ? value : fallback;
}

export function sortByPinned(items, pinnedIds) {
  const pinnedSet = new Set(ensureArray(pinnedIds, []).map((item) => String(item)));
  return [...ensureArray(items, [])].sort((left, right) => {
    const leftPinned = pinnedSet.has(String(left?.id));
    const rightPinned = pinnedSet.has(String(right?.id));

    if (leftPinned === rightPinned) {
      return 0;
    }

    return leftPinned ? -1 : 1;
  });
}

export function normalizeWorkbenchDraftPrompt(value, legacyPrompts = new Set()) {
  if (typeof value !== "string") {
    return "";
  }

  const normalized = value.trim();
  if (!normalized || legacyPrompts.has(normalized)) {
    return "";
  }

  return value;
}

export function inferWorkbenchTaskIntent(prompt, workingFiles, actionHints = [], targetHints = []) {
  if (typeof prompt !== "string") {
    return null;
  }

  const normalized = prompt.trim().toLowerCase();
  if (!normalized) {
    return null;
  }

  const hasAction = actionHints.some((hint) => normalized.includes(hint));
  const hasTarget = targetHints.some((hint) => normalized.includes(hint));

  if (hasAction && hasTarget) {
    return "workspace_organize";
  }

  return null;
}

export function cloneWorkspaceStats(items) {
  return ensureArray(items, []).map((item) => ({ ...item }));
}

export function cloneStringList(items) {
  return [...ensureArray(items, [])];
}

export function cloneGraphLinks(items) {
  return ensureArray(items, []).map((item) => ({ ...item }));
}

export function cloneWorkbenchNodes(nodes) {
  return ensureArray(nodes, []).map((node) => ({
    ...node,
    checks: ensureArray(node.checks, []).map((check) => ({ ...check })),
  }));
}

export function cloneSkillPermissions(skills) {
  return ensureArray(skills, []).map((skill) => ({
    ...skill,
    agents: ensureArray(skill.agents, []).map((agent) => ({ ...agent })),
  }));
}

export function normalizeWorkspaceItem(item) {
  if (!item) {
    return null;
  }

  if (typeof item === "string") {
    const label = item.split("/").pop() ?? item;
    return {
      path: item,
      label,
      kind: label.includes(".") ? "file" : "folder",
    };
  }

  const path = typeof item.path === "string" ? item.path : "";
  const label = typeof item.label === "string" && item.label ? item.label : path.split("/").pop() ?? "";
  const kind = item.kind === "file" ? "file" : "folder";

  if (!path || !label) {
    return null;
  }

  return { path, label, kind };
}

export function normalizeWorkspaceItemList(items, fallback = []) {
  return ensureArray(items, fallback)
    .map((item) => normalizeWorkspaceItem(item))
    .filter(Boolean);
}

export function normalizeWorkbenchRecord(record, index = 0) {
  if (!record || typeof record !== "object") {
    return null;
  }

  return {
    id: record.id ?? `seed-${index + 1}`,
    name: typeof record.name === "string" && record.name ? record.name : `工作区任务 ${index + 1}`,
    skills: typeof record.skills === "string" && record.skills ? record.skills : "检索 / 整理 / 审查",
    status: typeof record.status === "string" && record.status ? record.status : "已准备",
    statusTone: typeof record.statusTone === "string" ? record.statusTone : "warn",
    inspectMode: typeof record.inspectMode === "string" && record.inspectMode ? record.inspectMode : "重点",
    time: typeof record.time === "string" && record.time ? record.time : "",
  };
}

export function normalizeWorkbenchRecordList(items, fallback = []) {
  return ensureArray(items, fallback)
    .map((record, index) => normalizeWorkbenchRecord(record, index))
    .filter(Boolean);
}

export function normalizeAssistantSession(session, index = 0) {
  if (!session || typeof session !== "object") {
    return null;
  }

  const rawId = session.id ?? `assistant-${index + 1}`;
  const id = String(rawId);

  return {
    id,
    title: typeof session.title === "string" && session.title ? session.title : `会话 ${index + 1}`,
    summary: typeof session.summary === "string" && session.summary ? session.summary : "等待下一次提问",
    messageCount: Number.isFinite(session.messageCount) ? session.messageCount : undefined,
    createdAt: typeof session.createdAt === "string" ? session.createdAt : null,
    updatedAt: typeof session.updatedAt === "string" ? session.updatedAt : null,
  };
}

export function normalizeAssistantSessionList(items, fallback = []) {
  return ensureArray(items, fallback)
    .map((session, index) => normalizeAssistantSession(session, index))
    .filter(Boolean);
}

export function normalizeAssistantMessage(message, index = 0) {
  if (!message || typeof message !== "object") {
    return null;
  }

  return {
    id: String(message.id ?? `assistant-message-${index + 1}`),
    role: message.role === "assistant" ? "assistant" : "user",
    text: typeof message.text === "string" ? message.text : typeof message.content === "string" ? message.content : "",
    createdAt: typeof message.createdAt === "string" ? message.createdAt : null,
    cites: cloneStringList(message.cites),
    pending: Boolean(message.pending),
    error: Boolean(message.error),
  };
}

export function normalizeAssistantMessageList(items, fallback = []) {
  return ensureArray(items, fallback)
    .map((message, index) => normalizeAssistantMessage(message, index))
    .filter(Boolean);
}

export function createOptimisticAssistantMessage(role, text, options = {}) {
  return normalizeAssistantMessage({
    id: options.id ?? `assistant-temp-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    role,
    text,
    createdAt: new Date().toISOString(),
    cites: [],
    pending: Boolean(options.pending),
    error: Boolean(options.error),
  });
}

export function normalizeAssistantDetail(detail) {
  if (!detail || typeof detail !== "object") {
    return null;
  }

  return {
    id: String(detail.id),
    title: typeof detail.title === "string" && detail.title ? detail.title : "新对话",
    summary: typeof detail.summary === "string" && detail.summary ? detail.summary : "等待下一次提问",
    globalScope: Boolean(detail.globalScope),
    workingFiles: normalizeWorkspaceItemList(detail.workingFiles, []),
    createdAt: typeof detail.createdAt === "string" ? detail.createdAt : null,
    updatedAt: typeof detail.updatedAt === "string" ? detail.updatedAt : null,
    messages: normalizeAssistantMessageList(detail.messages, []),
  };
}

export function normalizeWorkbenchDetail(detail) {
  if (!detail || typeof detail !== "object") {
    return null;
  }

  const record = normalizeWorkbenchRecord(detail, 0);

  return {
    ...record,
    prompt: typeof detail.prompt === "string" ? detail.prompt : "",
    globalScope: Boolean(detail.globalScope),
    workingFiles: normalizeWorkspaceItemList(detail.workingFiles, []),
    selectedNodeId:
      typeof detail.selectedNodeId === "string" && detail.selectedNodeId ? detail.selectedNodeId : null,
    activeExecutionNodeId:
      typeof detail.activeExecutionNodeId === "string" && detail.activeExecutionNodeId
        ? detail.activeExecutionNodeId
        : null,
    activeInspectionNodeId:
      typeof detail.activeInspectionNodeId === "string" && detail.activeInspectionNodeId
        ? detail.activeInspectionNodeId
        : null,
    currentPage: Number.isFinite(detail.currentPage) ? Number(detail.currentPage) : 1,
    totalPages: Number.isFinite(detail.totalPages) ? Number(detail.totalPages) : 1,
    nodes: cloneWorkbenchNodes(detail.nodes),
    graphLinks: cloneGraphLinks(detail.graphLinks),
    graphMainPath: cloneStringList(detail.graphMainPath),
  };
}
