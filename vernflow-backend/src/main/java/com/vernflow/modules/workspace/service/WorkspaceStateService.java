package com.vernflow.modules.workspace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.workspace.domain.WorkspaceState;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.dto.WorkspaceStateSnapshot;
import com.vernflow.modules.workspace.dto.WorkspaceStateUpdateRequest;
import com.vernflow.modules.workspace.repository.WorkspaceStateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles persisted workspace-shell preferences and drafts.
 */
@Service
public class WorkspaceStateService {

    private static final TypeReference<List<WorkspaceItemRef>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private static final String DEFAULT_WORKING_FILES_JSON = "[]";
    private static final String DEFAULT_HOME_TASK_DRAFT = "整理当前工作区资料，并给出目录建议";
    private static final String DEFAULT_WORKBENCH_PROMPT = "基于右侧工作文件完成当前资料任务。";

    private final WorkspaceStateRepository workspaceStateRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceStateService(
            WorkspaceStateRepository workspaceStateRepository,
            ObjectMapper objectMapper
    ) {
        this.workspaceStateRepository = workspaceStateRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Load the persisted workspace-shell state or return defaults.
     *
     * @return current workspace state snapshot
     */
    @Transactional(readOnly = true)
    public WorkspaceStateSnapshot loadSnapshot(Long userId) {
        WorkspaceState state = workspaceStateRepository.findById(userId)
                .orElseGet(() -> defaultEntity(userId));
        return toSnapshot(state);
    }

    /**
     * Whether a real persisted workspace-shell state row already exists.
     *
     * @return true when the singleton state has been saved before
     */
    @Transactional(readOnly = true)
    public boolean hasPersistedState(Long userId) {
        return workspaceStateRepository.existsById(userId);
    }

    /**
     * Persist the latest workspace-shell state.
     *
     * @param request state update request
     * @return normalized snapshot after persistence
     */
    @Transactional
    public WorkspaceStateSnapshot updateState(Long userId, WorkspaceStateUpdateRequest request) {
        WorkspaceState state = workspaceStateRepository.findById(userId)
                .orElseGet(() -> defaultEntity(userId));

        state.update(
                serializeItems(request.workingFiles()),
                request.globalScope(),
                request.selectedTreePath(),
                normalizeBlank(request.homeTaskDraft(), DEFAULT_HOME_TASK_DRAFT),
                normalizeBlank(request.workbenchPrompt(), DEFAULT_WORKBENCH_PROMPT)
        );

        WorkspaceState saved = workspaceStateRepository.save(state);
        return toSnapshot(saved);
    }

    private WorkspaceState defaultEntity(Long userId) {
        return WorkspaceState.createDefault(
                userId,
                DEFAULT_WORKING_FILES_JSON,
                "workspace://",
                DEFAULT_HOME_TASK_DRAFT,
                DEFAULT_WORKBENCH_PROMPT
        );
    }

    private WorkspaceStateSnapshot toSnapshot(WorkspaceState state) {
        return new WorkspaceStateSnapshot(
                deserializeItems(state.getWorkingFilesJson()),
                state.isGlobalScope(),
                normalizeBlank(state.getSelectedTreePath(), "workspace://"),
                normalizeBlank(state.getHomeTaskDraft(), DEFAULT_HOME_TASK_DRAFT),
                normalizeBlank(state.getWorkbenchPrompt(), DEFAULT_WORKBENCH_PROMPT)
        );
    }

    private String serializeItems(List<WorkspaceItemRef> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to serialize workspace state.");
        }
    }

    private List<WorkspaceItemRef> deserializeItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, ITEM_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to read workspace state.");
        }
    }

    private String normalizeBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
