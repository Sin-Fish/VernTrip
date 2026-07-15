import { computed, reactive, watch } from "vue";
import { createDefaultWorkspaceBootstrap } from "@/data/mock";
import { userRequest } from "@/lib/userApi";
import * as appStateStorage from "@/composables/appStateStorage";
import { createAppStateLocalActions } from "@/composables/appStateLocalActions";
import * as appStateUtils from "@/composables/appStateUtils";

const defaults = createDefaultWorkspaceBootstrap();
const PINNED_ASSISTANT_STORAGE_KEY = "vernflow-pinned-assistant-sessions";
const PINNED_WORKBENCH_STORAGE_KEY = "vernflow-pinned-workbench-runs";
const WORKBENCH_VIEW_STORAGE_KEY = "vernflow-workbench-view";
const WORKBENCH_RUN_STORAGE_KEY = "vernflow-active-workbench-run";
const LEGACY_WORKBENCH_PROMPTS = new Set([
  defaults.workbenchPrompt,
  "基于右侧工作文件完成当前资料任务。",
]);

const WORKSPACE_ORGANIZE_ACTION_HINTS = [
  "整理",
  "归档",
  "分类",
  "归类",
  "重组",
  "收纳",
  "移动",
  "梳理",
  "organize",
  "archive",
  "classify",
  "sort",
  "move",
  "restructure",
];

const WORKSPACE_ORGANIZE_TARGET_HINTS = [
  "文件",
  "文件夹",
  "资料",
  "资料库",
  "资源",
  "目录",
  "工作区",
  "library",
  "workspace",
  "file",
  "files",
  "folder",
  "folders",
  "document",
  "documents",
];

async function requestJson(path, options = {}) {
  return userRequest(path, options);
}

const {
  ensureArray,
  sortByPinned,
  normalizeWorkbenchDraftPrompt,
  inferWorkbenchTaskIntent,
  cloneWorkspaceStats,
  cloneStringList,
  cloneGraphLinks,
  cloneWorkbenchNodes,
  cloneSkillPermissions,
  normalizeWorkspaceItemList,
  normalizeWorkbenchRecordList,
  normalizeAssistantSessionList,
  normalizeAssistantMessageList,
  createOptimisticAssistantMessage,
  normalizeAssistantDetail,
  normalizeWorkbenchDetail,
} = appStateUtils;

const dataState = reactive({
  homeSummary: { ...defaults.homeSummary },
  treeItems: appStateUtils.normalizeWorkspaceItemList(defaults.treeItems),
  workbenchRecords: appStateUtils.normalizeWorkbenchRecordList(defaults.workbenchRecords),
  workbenchNodes: appStateUtils.cloneWorkbenchNodes(defaults.workbenchNodes),
  workbenchDraftNodes: appStateUtils.cloneWorkbenchNodes(defaults.workbenchNodes),
  graphLinks: appStateUtils.cloneGraphLinks(defaults.graphLinks),
  workbenchDraftGraphLinks: appStateUtils.cloneGraphLinks(defaults.graphLinks),
  graphMainPath: appStateUtils.cloneStringList(defaults.graphMainPath),
  workbenchDraftGraphMainPath: appStateUtils.cloneStringList(defaults.graphMainPath),
  workbenchDraftSelectedNodeId: defaults.selectedNodeId,
  workbenchWorkspaceStats: appStateUtils.cloneWorkspaceStats(defaults.workbenchWorkspaceStats),
  chatSessions: [],
  chatMessages: [],
  settingsTabs: appStateUtils.ensureArray(defaults.settingsTabs, []).map((item) => ({ ...item })),
  skillLibraryRows: appStateUtils.ensureArray(defaults.skillLibraryRows, []).map((item) => ({
    ...item,
    actions: appStateUtils.cloneStringList(item.actions),
  })),
  activeWorkbenchRun: null,
});

const state = reactive({
  workingFiles: appStateUtils.normalizeWorkspaceItemList(defaults.workingFiles),
  globalScope: defaults.globalScope,
  selectedTreePath: defaults.selectedTreePath,
  selectedNodeId: null,
  selectedSessionId: null,
  settingTab: defaults.settingsTabs[0].id,
  workbenchView: appStateStorage.readWorkbenchView(WORKBENCH_VIEW_STORAGE_KEY),
  currentWorkbenchRunId: appStateStorage.readWorkbenchRunId(WORKBENCH_RUN_STORAGE_KEY),
  resourceCollapsed: {
    workbench: false,
    assistant: false,
  },
  sessionsCollapsed: false,
  skillModalOpen: false,
  nodeModalOpen: false,
  inspectResultVisible: false,
  skillPermissions: appStateUtils.cloneSkillPermissions(defaults.skillPermissions),
  homeTaskDraft: defaults.homeTaskDraft,
  workbenchPrompt: appStateUtils.normalizeWorkbenchDraftPrompt(defaults.workbenchPrompt, LEGACY_WORKBENCH_PROMPTS),
  workbenchPromptDirty: false,
  workbenchGenerateExecutionReport: false,
  workbenchActiveTaskLabel: "",
  assistantPrompt: "",
  bootstrapLoading: false,
  bootstrapLoaded: false,
  bootstrapError: null,
  workbenchLoading: false,
  workbenchSubmitting: false,
  workbenchError: null,
  assistantLoading: false,
  assistantSubmitting: false,
  assistantError: null,
  pinnedAssistantSessionIds: appStateStorage.readPinnedIds(PINNED_ASSISTANT_STORAGE_KEY),
  pinnedWorkbenchRunIds: appStateStorage.readPinnedIds(PINNED_WORKBENCH_STORAGE_KEY),
});

let persistTimer = null;
let workbenchPollTimer = null;
let applyingRemoteState = false;

function isRunningWorkbenchStatus(status) {
  return typeof status === "string" && status.trim().toLowerCase() === "running";
}

function stopWorkbenchPolling() {
  if (workbenchPollTimer !== null) {
    window.clearTimeout(workbenchPollTimer);
    workbenchPollTimer = null;
  }
}

function scheduleWorkbenchPolling(runId) {
  stopWorkbenchPolling();

  if (!runId) {
    return;
  }

  workbenchPollTimer = window.setTimeout(async () => {
    try {
      const data = await requestJson(`/api/workbench/runs/${runId}`);
      applyWorkbenchDetail(data);
      await loadWorkbenchRecords();

      if (isRunningWorkbenchStatus(data?.status)) {
        scheduleWorkbenchPolling(runId);
      } else {
        await refreshBootstrap();
      }
    } catch (error) {
      console.error("Failed to poll workbench run", error);
      stopWorkbenchPolling();
    }
  }, 900);
}

function applyWorkspaceState(snapshot) {
  applyingRemoteState = true;

  try {
    state.workingFiles = normalizeWorkspaceItemList(snapshot.workingFiles, state.workingFiles);
    state.globalScope = typeof snapshot.globalScope === "boolean" ? snapshot.globalScope : state.globalScope;
    state.selectedTreePath = snapshot.selectedTreePath ?? state.selectedTreePath;
    state.homeTaskDraft = snapshot.homeTaskDraft ?? state.homeTaskDraft;
  } finally {
    applyingRemoteState = false;
  }
}

function restoreWorkbenchDraftGraph() {
  stopWorkbenchPolling();
  applyingRemoteState = true;
  const nextSelectedNodeId =
    state.selectedNodeId && dataState.workbenchDraftNodes.some((node) => node.id === state.selectedNodeId)
      ? state.selectedNodeId
      : null;

  try {
    dataState.activeWorkbenchRun = null;
    dataState.workbenchNodes = cloneWorkbenchNodes(dataState.workbenchDraftNodes);
    dataState.graphLinks = cloneGraphLinks(dataState.workbenchDraftGraphLinks);
    dataState.graphMainPath = cloneStringList(dataState.workbenchDraftGraphMainPath);
    state.selectedNodeId = nextSelectedNodeId;
  } finally {
    applyingRemoteState = false;
  }
}

function applyWorkbenchDetail(detail) {
  const normalized = normalizeWorkbenchDetail(detail);

  if (!normalized) {
    return;
  }

  applyingRemoteState = true;
  const sameRunPage =
    dataState.activeWorkbenchRun?.id === normalized.id &&
    dataState.activeWorkbenchRun?.currentPage === normalized.currentPage;
  const nextSelectedNodeId =
    state.selectedNodeId && normalized.nodes.some((node) => node.id === state.selectedNodeId)
      ? state.selectedNodeId
      : null;

  try {
    dataState.activeWorkbenchRun = normalized;
    dataState.workbenchNodes = cloneWorkbenchNodes(normalized.nodes);
    dataState.graphLinks = cloneGraphLinks(normalized.graphLinks);
    dataState.graphMainPath = cloneStringList(normalized.graphMainPath);
    state.selectedNodeId = nextSelectedNodeId;
    if (!sameRunPage) {
      state.workbenchPrompt = "";
      state.workbenchPromptDirty = false;
    }
    state.workbenchActiveTaskLabel = isRunningWorkbenchStatus(normalized.status) ? normalized.prompt || state.workbenchActiveTaskLabel : "";
    state.currentWorkbenchRunId = normalized.id;
    state.workbenchView = "run";
    appStateStorage.writeWorkbenchRunId(WORKBENCH_RUN_STORAGE_KEY, normalized.id);
    appStateStorage.writeWorkbenchView(WORKBENCH_VIEW_STORAGE_KEY, "run");
  } finally {
    applyingRemoteState = false;
  }

  if (isRunningWorkbenchStatus(normalized.status) && normalized.currentPage === normalized.totalPages) {
    scheduleWorkbenchPolling(normalized.id);
  } else {
    stopWorkbenchPolling();
  }
}

function applyAssistantDetail(detail, { syncScope = true } = {}) {
  const normalized = normalizeAssistantDetail(detail);

  if (!normalized) {
    return;
  }

  applyingRemoteState = true;

  try {
    state.selectedSessionId = normalized.id;
    dataState.chatMessages = normalizeAssistantMessageList(normalized.messages, []);

    const sessionIndex = dataState.chatSessions.findIndex((session) => session.id === normalized.id);
    const sessionCard = normalizeAssistantSession(normalized);
    if (sessionIndex >= 0) {
      dataState.chatSessions.splice(sessionIndex, 1, sessionCard);
    } else {
      dataState.chatSessions.unshift(sessionCard);
    }

    if (syncScope) {
      state.globalScope = normalized.globalScope;
      state.workingFiles = normalizeWorkspaceItemList(normalized.workingFiles, state.workingFiles);
    }
  } finally {
    applyingRemoteState = false;
  }
}

async function requestBootstrap({ force = false } = {}) {
  if (state.bootstrapLoading || (!force && state.bootstrapLoaded)) {
    return;
  }

  state.bootstrapLoading = true;
  state.bootstrapError = null;

  try {
    const bootstrap = await requestJson("/api/workspace/bootstrap");
    applyBootstrap(bootstrap);
    state.bootstrapLoaded = true;
  } catch (error) {
    state.bootstrapError = error instanceof Error ? error.message : "\u5de5\u4f5c\u533a\u521d\u59cb\u5316\u8bf7\u6c42\u5931\u8d25\u3002";
  } finally {
    state.bootstrapLoading = false;
  }
}

async function loadBootstrap() {
  await requestBootstrap();
}

async function refreshBootstrap() {
  await requestBootstrap({ force: true });
}

function applyBootstrap(bootstrap) {
  if (!bootstrap || typeof bootstrap !== "object") {
    return;
  }

  applyingRemoteState = true;

  try {
    const nextTreeItems = normalizeWorkspaceItemList(bootstrap.treeItems, dataState.treeItems);
    const nextWorkbenchRecords = normalizeWorkbenchRecordList(bootstrap.workbenchRecords, dataState.workbenchRecords);
    const nextWorkbenchNodes = cloneWorkbenchNodes(bootstrap.workbenchNodes ?? dataState.workbenchDraftNodes);
    const nextGraphLinks = cloneGraphLinks(bootstrap.graphLinks ?? dataState.workbenchDraftGraphLinks);
    const nextGraphMainPath = cloneStringList(bootstrap.graphMainPath ?? dataState.workbenchDraftGraphMainPath);
    const nextSelectedNodeId =
      state.selectedNodeId && nextWorkbenchNodes.some((node) => node.id === state.selectedNodeId)
        ? state.selectedNodeId
        : null;
    const nextChatSessions = normalizeAssistantSessionList(bootstrap.chatSessions, dataState.chatSessions);
    const nextBootstrapSessionId = bootstrap.selectedSessionId == null ? null : String(bootstrap.selectedSessionId);
    const nextChatMessages = normalizeAssistantMessageList(bootstrap.chatMessages, dataState.chatMessages);
    const preservedAssistantSelection =
      state.selectedSessionId &&
      nextChatSessions.some((session) => session.id === state.selectedSessionId)
        ? state.selectedSessionId
        : null;
    dataState.homeSummary = bootstrap.homeSummary ?? dataState.homeSummary;
    dataState.treeItems = nextTreeItems;
    dataState.workbenchRecords = nextWorkbenchRecords;
    dataState.workbenchDraftNodes = nextWorkbenchNodes;
    dataState.workbenchDraftGraphLinks = nextGraphLinks;
    dataState.workbenchDraftGraphMainPath = nextGraphMainPath;
    dataState.workbenchDraftSelectedNodeId = nextSelectedNodeId;
    dataState.workbenchWorkspaceStats = cloneWorkspaceStats(
      bootstrap.workbenchWorkspaceStats ?? dataState.workbenchWorkspaceStats,
    );
    dataState.chatSessions = nextChatSessions;
    dataState.settingsTabs = ensureArray(bootstrap.settingsTabs, dataState.settingsTabs).map((item) => ({ ...item }));
    dataState.skillLibraryRows = ensureArray(bootstrap.skillLibraryRows, dataState.skillLibraryRows).map((item) => ({
      ...item,
      actions: cloneStringList(item.actions),
    }));

    state.workingFiles = normalizeWorkspaceItemList(bootstrap.workingFiles, state.workingFiles);
    state.globalScope = typeof bootstrap.globalScope === "boolean" ? bootstrap.globalScope : state.globalScope;
    state.selectedTreePath =
      bootstrap.selectedTreePath ??
      nextTreeItems[0]?.path ??
      state.selectedTreePath;
    state.selectedSessionId =
      preservedAssistantSelection ??
      (nextBootstrapSessionId && nextChatSessions.some((session) => session.id === nextBootstrapSessionId)
        ? nextBootstrapSessionId
        : nextChatSessions[0]?.id ?? null);
    state.settingTab = dataState.settingsTabs[0]?.id ?? state.settingTab;
    state.skillPermissions = cloneSkillPermissions(
      ensureArray(bootstrap.skillPermissions, state.skillPermissions),
    );
    state.homeTaskDraft = bootstrap.homeTaskDraft ?? state.homeTaskDraft;

    if (!(state.workbenchView === "run" && dataState.activeWorkbenchRun)) {
      dataState.workbenchNodes = cloneWorkbenchNodes(nextWorkbenchNodes);
      dataState.graphLinks = cloneGraphLinks(nextGraphLinks);
      dataState.graphMainPath = cloneStringList(nextGraphMainPath);
      state.selectedNodeId = nextSelectedNodeId;
    }

    if (!preservedAssistantSelection || preservedAssistantSelection === nextBootstrapSessionId || !dataState.chatMessages.length) {
      dataState.chatMessages = nextChatMessages;
    }
  } finally {
    applyingRemoteState = false;
  }
}

async function persistWorkspaceState() {
  if (!state.bootstrapLoaded || state.bootstrapLoading || applyingRemoteState) {
    return;
  }

  try {
    const data = await requestJson("/api/workspace/state", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        workingFiles: state.workingFiles,
        globalScope: state.globalScope,
        selectedTreePath: state.selectedTreePath,
        homeTaskDraft: state.homeTaskDraft,
      }),
    });
    applyWorkspaceState(data);
  } catch (error) {
    console.error("Failed to persist workspace state", error);
  }
}

function scheduleWorkspacePersist() {
  if (persistTimer !== null) {
    window.clearTimeout(persistTimer);
  }

  persistTimer = window.setTimeout(() => {
    persistWorkspaceState();
  }, 180);
}

const {
  isAssistantSessionPinned,
  isWorkbenchRunPinned,
  togglePinnedAssistantSession,
  togglePinnedWorkbenchRun,
  addWorkingFile,
  removeWorkingFile,
  selectTreeItem,
  selectNode,
  clearSelectedNode,
  openNodeModal,
  closeNodeModal,
  runInspect,
  toggleGlobalScope,
  toggleResource,
  toggleSessions,
  openSkillModal,
  closeSkillModal,
  toggleSkillRow,
  openAssistantDraft,
  openWorkbenchDraft,
  markWorkbenchPromptDirty,
  backWorkbenchList,
  resetWorkbenchView,
} = createAppStateLocalActions({
  state,
  dataState,
  appStateStorage,
  normalizeWorkspaceItem: appStateUtils.normalizeWorkspaceItem,
  scheduleWorkspacePersist,
  stopWorkbenchPolling,
  restoreWorkbenchDraftGraph,
  storageKeys: {
    pinnedAssistantStorageKey: PINNED_ASSISTANT_STORAGE_KEY,
    pinnedWorkbenchStorageKey: PINNED_WORKBENCH_STORAGE_KEY,
    workbenchViewStorageKey: WORKBENCH_VIEW_STORAGE_KEY,
    workbenchRunStorageKey: WORKBENCH_RUN_STORAGE_KEY,
  },
});

watch(
  () => state.homeTaskDraft,
  () => {
    if (!applyingRemoteState) {
      scheduleWorkspacePersist();
    }
  },
);

const homeSummary = computed(() => dataState.homeSummary);
const treeItems = computed(() => dataState.treeItems);
const workbenchRecords = computed(() => sortByPinned(dataState.workbenchRecords, state.pinnedWorkbenchRunIds));
const workbenchNodes = computed(() => dataState.workbenchNodes);
const graphLinks = computed(() => dataState.graphLinks);
const graphMainPath = computed(() => dataState.graphMainPath);
const workbenchWorkspaceStats = computed(() => dataState.workbenchWorkspaceStats);
const chatSessions = computed(() => sortByPinned(dataState.chatSessions, state.pinnedAssistantSessionIds));
const chatMessages = computed(() => dataState.chatMessages);
const settingsTabs = computed(() => dataState.settingsTabs);
const skillLibraryRows = computed(() => dataState.skillLibraryRows);
const activeWorkbenchRun = computed(() => dataState.activeWorkbenchRun);

const selectedNode = computed(() => {
  if (!state.selectedNodeId) {
    return null;
  }

  return dataState.workbenchNodes.find((node) => node.id === state.selectedNodeId) ?? null;
});

const selectedSession = computed(() => {
  return dataState.chatSessions.find((session) => session.id === state.selectedSessionId) ?? dataState.chatSessions[0] ?? null;
});

const scopeLabel = computed(() => {
  if (state.globalScope) {
    return "当前范围：全局工作区 · 工作文件仍会保留";
  }

  return `当前范围：右侧工作文件 · ${state.workingFiles.length} 份资料`;
});

const nodeScopeLabel = computed(() => {
  return state.globalScope ? "全局工作区" : "工作文件";
});

const nodeLog = computed(() => {
  const node = selectedNode.value;
  if (!node) {
    return "";
  }

  return JSON.stringify(
    {
      node: node?.title ?? null,
      type: node?.type ?? null,
      status: node?.status ?? null,
      scope: state.globalScope ? "global_workspace" : "working_files",
      refs: state.workingFiles.map((item) => ({
        path: item.path,
        label: item.label,
        kind: item.kind,
      })),
    },
    null,
    2,
  );
});

async function loadWorkbenchRecords() {
  try {
    const data = await requestJson("/api/workbench/runs");
    dataState.workbenchRecords = normalizeWorkbenchRecordList(data, dataState.workbenchRecords);
  } catch (error) {
    console.error("Failed to load workbench runs", error);
  }
}

async function loadAssistantSessions({ autoOpen = false } = {}) {
  try {
    const data = await requestJson("/api/assistant/sessions");
    const sessions = normalizeAssistantSessionList(data, dataState.chatSessions);
    dataState.chatSessions = sessions;

    if (autoOpen && !state.selectedSessionId && !dataState.chatMessages.length && sessions.length) {
      await openAssistantSession(sessions[0].id);
    }
  } catch (error) {
    console.error("Failed to load assistant sessions", error);
  }
}

async function deleteAssistantSession(sessionId) {
  if (!sessionId) {
    return;
  }

  const deletingCurrent = state.selectedSessionId === String(sessionId);

  try {
    await requestJson(`/api/assistant/sessions/${sessionId}`, {
      method: "DELETE",
    });

    state.pinnedAssistantSessionIds = state.pinnedAssistantSessionIds.filter((item) => item !== String(sessionId));
    appStateStorage.writePinnedIds(PINNED_ASSISTANT_STORAGE_KEY, state.pinnedAssistantSessionIds);

    dataState.chatSessions = dataState.chatSessions.filter((session) => session.id !== String(sessionId));

    if (deletingCurrent) {
      const nextSession = dataState.chatSessions[0];
      if (nextSession) {
        await openAssistantSession(nextSession.id);
      } else {
        openAssistantDraft();
      }
    }

    await loadAssistantSessions();
  } catch (error) {
    state.assistantError = error instanceof Error ? error.message : "\u5220\u9664\u4f1a\u8bdd\u5931\u8d25\u3002";
    console.error("Failed to delete assistant session", error);
  }
}

async function openAssistantSession(sessionId) {
  if (!sessionId) {
    openAssistantDraft();
    return;
  }

  state.assistantLoading = true;
  state.assistantError = null;

  try {
    const data = await requestJson(`/api/assistant/sessions/${sessionId}`);
    applyAssistantDetail(data, { syncScope: true });
    scheduleWorkspacePersist();
  } catch (error) {
    state.assistantError = error instanceof Error ? error.message : "\u52a0\u8f7d\u9009\u4e2d\u4f1a\u8bdd\u5931\u8d25\u3002";
    console.error("Failed to open assistant session", error);
  } finally {
    state.assistantLoading = false;
  }
}

async function submitAssistantPrompt() {
  const prompt = state.assistantPrompt.trim();

  if (!prompt || state.assistantSubmitting) {
    return;
  }

  const optimisticUserMessage = createOptimisticAssistantMessage("user", prompt, {
    id: `assistant-user-${Date.now()}`,
  });
  const optimisticAssistantMessage = createOptimisticAssistantMessage("assistant", "\u6b63\u5728\u601d\u8003...", {
    id: `assistant-pending-${Date.now()}`,
    pending: true,
  });

  state.assistantSubmitting = true;
  state.assistantError = null;
  state.assistantPrompt = "";
  dataState.chatMessages = normalizeAssistantMessageList([
    ...dataState.chatMessages,
    optimisticUserMessage,
    optimisticAssistantMessage,
  ]);

  try {
    const data = await requestJson("/api/assistant/sessions/ask", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        sessionId: state.selectedSessionId ? Number(state.selectedSessionId) : null,
        prompt,
        globalScope: state.globalScope,
        workingFiles: state.workingFiles,
      }),
    });
    applyAssistantDetail(data, { syncScope: true });
    state.assistantPrompt = "";
    await loadAssistantSessions();
  } catch (error) {
    state.assistantError = error instanceof Error ? error.message : "\u53d1\u9001\u6d88\u606f\u5931\u8d25\u3002";
    dataState.chatMessages = dataState.chatMessages.map((message) => {
      if (message.id === optimisticAssistantMessage.id) {
        return normalizeAssistantMessage({
          ...message,
          text: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
          pending: false,
          error: true,
        });
      }

      if (message.id === optimisticUserMessage.id) {
        return normalizeAssistantMessage({
          ...message,
          pending: false,
        });
      }

      return message;
    });
    console.error("Failed to ask assistant", error);
  } finally {
    state.assistantSubmitting = false;
  }
}

function toggleAllSkillAgents(enabled) {
  state.skillPermissions.forEach((skill) => {
    skill.agents.forEach((agent) => {
      agent.enabled = enabled;
    });
  });
}

async function openWorkbenchRun(runId = null, page = null) {
  state.workbenchError = null;

  if (typeof runId !== "number") {
    restoreWorkbenchDraftGraph();
    state.workbenchView = "run";
    appStateStorage.writeWorkbenchView(WORKBENCH_VIEW_STORAGE_KEY, "run");
    return;
  }

  const openingDifferentRun = dataState.activeWorkbenchRun?.id !== runId;
  const openingDifferentPage =
    typeof page === "number" && dataState.activeWorkbenchRun?.id === runId && dataState.activeWorkbenchRun?.currentPage !== page;

  if (openingDifferentRun || openingDifferentPage) {
    state.workbenchPrompt = "";
    state.workbenchPromptDirty = false;
  }

  state.workbenchLoading = true;

  try {
    const pageQuery = Number.isFinite(page) ? `?page=${Number(page)}` : "";
    const data = await requestJson(`/api/workbench/runs/${runId}${pageQuery}`);
    applyWorkbenchDetail(data);
  } catch (error) {
    state.workbenchError = error instanceof Error ? error.message : "\u5de5\u4f5c\u53f0\u8be6\u60c5\u8bf7\u6c42\u5931\u8d25\u3002";
    console.error("Failed to open workbench run", error);
  } finally {
    state.workbenchLoading = false;
  }
}

async function submitWorkbenchRun(executionMode) {
  const prompt = state.workbenchPrompt.trim();

  if (!prompt || state.workbenchSubmitting) {
    return;
  }

  const previousPrompt = state.workbenchPrompt;
  state.workbenchSubmitting = true;
  state.workbenchError = null;
  state.workbenchActiveTaskLabel = prompt;
  state.workbenchPrompt = "";
  state.workbenchPromptDirty = false;

  try {
    const currentRunId =
      dataState.activeWorkbenchRun && typeof dataState.activeWorkbenchRun.id === "number"
        ? dataState.activeWorkbenchRun.id
        : state.currentWorkbenchRunId;
    const taskIntent = inferWorkbenchTaskIntent(
      prompt,
      state.workingFiles,
      WORKSPACE_ORGANIZE_ACTION_HINTS,
      WORKSPACE_ORGANIZE_TARGET_HINTS,
    );
    const data = await requestJson(currentRunId == null ? "/api/workbench/runs" : `/api/workbench/runs/${currentRunId}`, {
      method: currentRunId == null ? "POST" : "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        prompt,
        workingFiles: state.workingFiles,
        globalScope: state.globalScope,
        executionMode,
        taskIntent,
        generateExecutionReport: state.workbenchGenerateExecutionReport,
      }),
    });
    applyWorkbenchDetail(data);
    state.workbenchView = "run";
    await loadWorkbenchRecords();
    if (!isRunningWorkbenchStatus(data?.status)) {
      state.workbenchActiveTaskLabel = "";
      await refreshBootstrap();
    }
  } catch (error) {
    state.workbenchPrompt = previousPrompt;
    state.workbenchPromptDirty = true;
    state.workbenchActiveTaskLabel = "";
    state.workbenchError = error instanceof Error ? error.message : "\u5de5\u4f5c\u53f0\u4efb\u52a1\u521b\u5efa\u5931\u8d25\u3002";
    console.error("Failed to create workbench run", error);
  } finally {
    state.workbenchSubmitting = false;
  }
}

async function deleteWorkbenchRun(runId) {
  if (typeof runId !== "number") {
    return;
  }

  const deletingActive = dataState.activeWorkbenchRun?.id === runId;

  try {
    await requestJson(`/api/workbench/runs/${runId}`, {
      method: "DELETE",
    });

    state.pinnedWorkbenchRunIds = state.pinnedWorkbenchRunIds.filter((item) => item !== String(runId));
    appStateStorage.writePinnedIds(PINNED_WORKBENCH_STORAGE_KEY, state.pinnedWorkbenchRunIds);

    if (deletingActive) {
      resetWorkbenchView();
    } else if (state.currentWorkbenchRunId === runId) {
      state.currentWorkbenchRunId = null;
      appStateStorage.writeWorkbenchRunId(WORKBENCH_RUN_STORAGE_KEY, null);
    }

    await loadWorkbenchRecords();
  } catch (error) {
    state.workbenchError = error instanceof Error ? error.message : "\u5220\u9664\u5de5\u4f5c\u53f0\u8bb0\u5f55\u5931\u8d25\u3002";
    console.error("Failed to delete workbench run", error);
  }
}

async function restoreWorkbenchRunView() {
  if (
    state.workbenchView !== "run" ||
    dataState.activeWorkbenchRun ||
    typeof state.currentWorkbenchRunId !== "number"
  ) {
    return;
  }

  await openWorkbenchRun(state.currentWorkbenchRunId);
}

function selectSession(sessionId) {
  openAssistantSession(sessionId);
}

export function useAppState() {
  return {
    state,
    homeSummary,
    treeItems,
    workbenchRecords,
    workbenchNodes,
    graphLinks,
    graphMainPath,
    workbenchWorkspaceStats,
    chatSessions,
    chatMessages,
    settingsTabs,
    skillLibraryRows,
    selectedNode,
    selectedSession,
    activeWorkbenchRun,
    scopeLabel,
    nodeScopeLabel,
    nodeLog,
    loadBootstrap,
    refreshBootstrap,
    loadWorkbenchRecords,
    loadAssistantSessions,
    deleteAssistantSession,
    isAssistantSessionPinned,
    togglePinnedAssistantSession,
    addWorkingFile,
    removeWorkingFile,
    selectTreeItem,
    selectNode,
    clearSelectedNode,
    openNodeModal,
    closeNodeModal,
    runInspect,
    toggleGlobalScope,
    toggleResource,
    toggleSessions,
    openSkillModal,
    closeSkillModal,
    toggleSkillRow,
    toggleAllSkillAgents,
    openAssistantDraft,
    openAssistantSession,
    submitAssistantPrompt,
    openWorkbenchDraft,
    markWorkbenchPromptDirty,
    openWorkbenchRun,
    submitWorkbenchRun,
    deleteWorkbenchRun,
    isWorkbenchRunPinned,
    togglePinnedWorkbenchRun,
    backWorkbenchList,
    resetWorkbenchView,
    restoreWorkbenchRunView,
    selectSession,
  };
}
