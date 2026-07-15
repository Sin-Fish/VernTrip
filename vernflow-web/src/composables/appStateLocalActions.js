export function createAppStateLocalActions({
  state,
  dataState,
  appStateStorage,
  normalizeWorkspaceItem,
  scheduleWorkspacePersist,
  stopWorkbenchPolling,
  restoreWorkbenchDraftGraph,
  storageKeys,
}) {
  const {
    pinnedAssistantStorageKey,
    pinnedWorkbenchStorageKey,
    workbenchViewStorageKey,
    workbenchRunStorageKey,
  } = storageKeys;

  function togglePinnedIds(pinnedIds, targetId) {
    const normalizedId = String(targetId);

    if (pinnedIds.includes(normalizedId)) {
      return pinnedIds.filter((item) => item !== normalizedId);
    }

    return [normalizedId, ...pinnedIds];
  }

  function isAssistantSessionPinned(sessionId) {
    return state.pinnedAssistantSessionIds.includes(String(sessionId));
  }

  function isWorkbenchRunPinned(runId) {
    return state.pinnedWorkbenchRunIds.includes(String(runId));
  }

  function togglePinnedAssistantSession(sessionId) {
    state.pinnedAssistantSessionIds = togglePinnedIds(state.pinnedAssistantSessionIds, sessionId);
    appStateStorage.writePinnedIds(pinnedAssistantStorageKey, state.pinnedAssistantSessionIds);
  }

  function togglePinnedWorkbenchRun(runId) {
    state.pinnedWorkbenchRunIds = togglePinnedIds(state.pinnedWorkbenchRunIds, runId);
    appStateStorage.writePinnedIds(pinnedWorkbenchStorageKey, state.pinnedWorkbenchRunIds);
  }

  function addWorkingFile(item) {
    const nextItem = normalizeWorkspaceItem(item);

    if (!nextItem || state.workingFiles.some((entry) => entry.path === nextItem.path)) {
      return;
    }

    state.workingFiles.push(nextItem);
    scheduleWorkspacePersist();
  }

  function removeWorkingFile(item) {
    const targetItem = normalizeWorkspaceItem(item);
    const index = state.workingFiles.findIndex((entry) => entry.path === targetItem?.path);

    if (index >= 0) {
      state.workingFiles.splice(index, 1);
      scheduleWorkspacePersist();
    }
  }

  function selectTreeItem(item) {
    const nextItem = normalizeWorkspaceItem(item);

    if (!nextItem) {
      return;
    }

    state.selectedTreePath = nextItem.path;
    scheduleWorkspacePersist();
  }

  function selectNode(nodeId) {
    if (!nodeId || state.selectedNodeId === nodeId) {
      clearSelectedNode();
      return;
    }

    state.selectedNodeId = nodeId;
    state.nodeModalOpen = false;
    state.inspectResultVisible = false;
  }

  function clearSelectedNode() {
    state.selectedNodeId = null;
    state.nodeModalOpen = false;
    state.inspectResultVisible = false;
  }

  function openNodeModal(nodeId) {
    state.selectedNodeId = nodeId;
    state.inspectResultVisible = false;
    state.nodeModalOpen = true;
  }

  function closeNodeModal() {
    state.nodeModalOpen = false;
    state.inspectResultVisible = false;
  }

  function runInspect() {
    state.inspectResultVisible = true;
  }

  function toggleGlobalScope(value) {
    state.globalScope = typeof value === "boolean" ? value : !state.globalScope;
    scheduleWorkspacePersist();
  }

  function toggleResource(pageKey) {
    state.resourceCollapsed[pageKey] = !state.resourceCollapsed[pageKey];
  }

  function toggleSessions() {
    state.sessionsCollapsed = !state.sessionsCollapsed;
  }

  function openSkillModal() {
    state.skillModalOpen = true;
  }

  function closeSkillModal() {
    state.skillModalOpen = false;
  }

  function toggleSkillRow(skillId) {
    const skill = state.skillPermissions.find((item) => item.id === skillId);

    if (skill) {
      skill.open = !skill.open;
    }
  }

  function openAssistantDraft() {
    state.selectedSessionId = null;
    state.assistantPrompt = "";
    state.assistantError = null;
    dataState.chatMessages = [];
  }

  function openWorkbenchDraft() {
    stopWorkbenchPolling();
    state.currentWorkbenchRunId = null;
    appStateStorage.writeWorkbenchRunId(workbenchRunStorageKey, null);
    restoreWorkbenchDraftGraph();
    state.workbenchPrompt = "";
    state.workbenchPromptDirty = false;
    state.workbenchGenerateExecutionReport = false;
    state.workbenchActiveTaskLabel = "";
    state.workbenchView = "run";
    appStateStorage.writeWorkbenchView(workbenchViewStorageKey, "run");
  }

  function markWorkbenchPromptDirty() {
    state.workbenchPromptDirty = true;
  }

  function backWorkbenchList() {
    clearSelectedNode();
    state.workbenchView = "list";
    appStateStorage.writeWorkbenchView(workbenchViewStorageKey, "list");
  }

  function resetWorkbenchView() {
    stopWorkbenchPolling();
    clearSelectedNode();
    restoreWorkbenchDraftGraph();
    state.workbenchError = null;
    state.currentWorkbenchRunId = null;
    state.workbenchPrompt = "";
    state.workbenchPromptDirty = false;
    state.workbenchGenerateExecutionReport = false;
    state.workbenchActiveTaskLabel = "";
    state.workbenchView = "list";
    appStateStorage.writeWorkbenchRunId(workbenchRunStorageKey, null);
    appStateStorage.writeWorkbenchView(workbenchViewStorageKey, "list");
  }

  return {
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
  };
}
