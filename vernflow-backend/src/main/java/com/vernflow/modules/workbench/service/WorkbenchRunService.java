package com.vernflow.modules.workbench.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.rag.service.RagProfileFacade;
import com.vernflow.modules.workbench.domain.WorkbenchRun;
import com.vernflow.modules.workbench.domain.WorkbenchRunStatus;
import com.vernflow.modules.workbench.dto.WorkbenchRunCreateRequest;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;
import com.vernflow.modules.workbench.model.WorkbenchPlan;
import com.vernflow.modules.workbench.model.WorkbenchRunHistoryPage;
import com.vernflow.modules.workbench.model.WorkbenchRunRuntime;
import com.vernflow.modules.workbench.repository.WorkbenchRunRepository;
import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Coordinates workbench run persistence, revision history, and execution lifecycle around a validated plan graph.
 */
@Service
public class WorkbenchRunService {
    private static final String WORKSPACE_ORGANIZE_INTENT = "workspace_organize";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final List<String> WORKSPACE_ORGANIZE_ACTION_HINTS = List.of(
            "整理", "归档", "分类", "归类", "重组", "收纳", "移动", "梳理",
            "organize", "archive", "classify", "sort", "move", "restructure"
    );
    private static final List<String> WORKSPACE_ORGANIZE_TARGET_HINTS = List.of(
            "文件", "文件夹", "资料", "资料库", "资源", "目录",
            "file", "files", "folder", "folders", "library", "archive", "archives"
    );

    private static final TypeReference<List<WorkspaceItemRef>> WORKSPACE_ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<WorkbenchPlan> WORKBENCH_PLAN_TYPE = new TypeReference<>() {
    };

    private final WorkbenchRunRepository workbenchRunRepository;
    private final ObjectMapper objectMapper;
    private final WorkbenchDecisionAgentService workbenchDecisionAgentService;
    private final WorkbenchAgentRunnerService workbenchAgentRunnerService;
    private final RagProfileFacade ragProfileFacade;
    private final WorkbenchRunHistoryService workbenchRunHistoryService;
    private final WorkbenchRunPresentationService workbenchRunPresentationService;

    public WorkbenchRunService(
            WorkbenchRunRepository workbenchRunRepository,
            ObjectMapper objectMapper,
            WorkbenchDecisionAgentService workbenchDecisionAgentService,
            WorkbenchAgentRunnerService workbenchAgentRunnerService,
            RagProfileFacade ragProfileFacade,
            WorkbenchRunHistoryService workbenchRunHistoryService,
            WorkbenchRunPresentationService workbenchRunPresentationService
    ) {
        this.workbenchRunRepository = workbenchRunRepository;
        this.objectMapper = objectMapper;
        this.workbenchDecisionAgentService = workbenchDecisionAgentService;
        this.workbenchAgentRunnerService = workbenchAgentRunnerService;
        this.ragProfileFacade = ragProfileFacade;
        this.workbenchRunHistoryService = workbenchRunHistoryService;
        this.workbenchRunPresentationService = workbenchRunPresentationService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceBootstrapResponse.WorkbenchRecord> listWorkbenchRecords(Long userId) {
        return workbenchRunRepository.findTop12ByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(run -> workbenchRunPresentationService.toRecord(run, resolvePlan(run)))
                .toList();
    }

    @Transactional
    public WorkbenchRunDetailResponse createRun(Long userId, WorkbenchRunCreateRequest request) {
        return saveRun(userId, null, request);
    }

    @Transactional
    public WorkbenchRunDetailResponse updateRun(Long userId, Long runId, WorkbenchRunCreateRequest request) {
        return saveRun(userId, runId, request);
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse loadRunDetail(Long userId, Long runId, Integer page) {
        WorkbenchRun run = workbenchRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException("Workbench run not found."));
        return toDetail(run, resolvePlan(run), page);
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse loadRunDetail(Long userId, Long runId) {
        return loadRunDetail(userId, runId, null);
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse loadRunDetail(Long runId, Integer page) {
        WorkbenchRun run = workbenchRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException("Workbench run not found."));
        return toDetail(run, resolvePlan(run), page);
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse loadRunDetail(Long runId) {
        return loadRunDetail(runId, (Integer) null);
    }

    @Transactional
    public void deleteRun(Long userId, Long runId) {
        WorkbenchRun run = workbenchRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException("Workbench run not found."));
        workbenchAgentRunnerService.stopRun(runId);
        workbenchRunRepository.delete(run);
    }

    private WorkbenchRunDetailResponse saveRun(Long userId, Long runId, WorkbenchRunCreateRequest request) {
        List<WorkspaceItemRef> workingFiles = normalizeWorkingFiles(request.workingFiles());
        boolean globalScope = Boolean.TRUE.equals(request.globalScope());
        String prompt = normalizePrompt(request.prompt(), workingFiles, globalScope);
        String taskIntent = resolveTaskIntent(request.taskIntent(), prompt);
        boolean generateExecutionReport = Boolean.TRUE.equals(request.generateExecutionReport());
        WorkbenchPlan plan = isWorkspaceOrganizationIntent(taskIntent)
                ? workbenchDecisionAgentService.planWorkspaceOrganization(
                        userId,
                        prompt,
                        globalScope,
                        workingFiles,
                        generateExecutionReport
                )
                : workbenchDecisionAgentService.plan(userId, prompt, globalScope, workingFiles);
        WorkbenchRunStatus status = resolveStatus(request.executionMode());
        String inspectMode = resolveInspectMode(request.executionMode(), plan.inspectMode());
        String name = buildRunName(request.prompt(), workingFiles);
        WorkbenchRunRuntime runtime = workbenchAgentRunnerService.initializeRuntime(plan);
        String ragProfileId = ragProfileFacade.defaultProfileId();

        String workingFilesJson = serializeWorkingFiles(workingFiles);
        String planJson = serializePlan(plan);
        String runtimeJson = workbenchAgentRunnerService.serializeRuntime(runtime);

        WorkbenchRun saved;
        if (runId == null) {
            saved = WorkbenchRun.create(
                    userId,
                    name,
                    prompt,
                    globalScope,
                    inspectMode,
                    plan.toolChain(),
                    plan.summary(),
                    planJson,
                    runtimeJson,
                    "[]",
                    ragProfileId,
                    workingFilesJson,
                    status
            );
        } else {
            workbenchAgentRunnerService.stopRun(runId);
            saved = workbenchRunRepository.findByIdAndUserId(runId, userId)
                    .orElseThrow(() -> new BusinessException("Workbench run not found."));
            String historyJson = workbenchRunHistoryService.appendHistoryPage(
                    saved.getHistoryJson(),
                    toDetail(saved, resolvePlan(saved))
            );
            saved.revise(
                    name,
                    prompt,
                    globalScope,
                    inspectMode,
                    plan.toolChain(),
                    plan.summary(),
                    planJson,
                    runtimeJson,
                    historyJson,
                    ragProfileId,
                    workingFilesJson,
                    status
            );
        }

        saved = workbenchRunRepository.save(saved);
        if (status == WorkbenchRunStatus.RUNNING) {
            workbenchAgentRunnerService.restartRun(saved.getId());
            saved = workbenchRunRepository.findById(saved.getId()).orElse(saved);
        }

        return toDetail(saved, plan);
    }

    private WorkbenchRunDetailResponse toDetail(WorkbenchRun run, WorkbenchPlan plan) {
        return toDetail(run, plan, null);
    }

    private WorkbenchRunDetailResponse toDetail(WorkbenchRun run, WorkbenchPlan plan, Integer requestedPage) {
        List<WorkspaceItemRef> workingFiles = deserializeWorkingFiles(run.getWorkingFilesJson());
        WorkbenchRunRuntime runtime = resolveRuntime(run, plan);
        String defaultSelectedNodeId = resolveSelectedNodeId(run, plan, runtime);
        return workbenchRunPresentationService.toDetail(
                run,
                plan,
                workingFiles,
                runtime,
                workbenchRunHistoryService.deserializeHistoryPages(run.getHistoryJson()),
                requestedPage,
                defaultSelectedNodeId
        );
    }

    private WorkbenchRunDetailResponse toDetail(
            WorkbenchRunHistoryPage page,
            Long runId,
            int currentPage,
            int totalPages
    ) {
        return workbenchRunPresentationService.toDetail(page, runId, currentPage, totalPages);
    }

    private WorkbenchPlan resolvePlan(WorkbenchRun run) {
        WorkbenchPlan plan = deserializePlan(run.getPlanJson());
        if (plan != null && !plan.nodes().isEmpty()) {
            return plan;
        }

        List<WorkspaceItemRef> workingFiles = deserializeWorkingFiles(run.getWorkingFilesJson());
        return workbenchDecisionAgentService.plan(
                run.getUserId(),
                run.getPrompt(),
                run.isGlobalScope(),
                workingFiles
        );
    }

    private WorkbenchRunRuntime resolveRuntime(WorkbenchRun run, WorkbenchPlan plan) {
        WorkbenchRunRuntime runtime = deserializeRuntime(run.getRuntimeJson());
        if (runtime != null && !runtime.getNodes().isEmpty()) {
            return runtime;
        }
        return workbenchAgentRunnerService.initializeRuntime(plan);
    }

    private String resolveSelectedNodeId(WorkbenchRun run, WorkbenchPlan plan, WorkbenchRunRuntime runtime) {
        if (StringUtils.hasText(runtime.getActiveExecutionNodeId())) {
            return runtime.getActiveExecutionNodeId();
        }
        if (StringUtils.hasText(runtime.getActiveInspectionNodeId())) {
            return runtime.getActiveInspectionNodeId();
        }
        if (run.getStatus() == WorkbenchRunStatus.PREPARED) {
            return plan.mainPath().isEmpty() ? plan.selectedNodeId() : plan.mainPath().getFirst();
        }
        return plan.selectedNodeId();
    }

    private List<WorkspaceItemRef> normalizeWorkingFiles(List<WorkspaceItemRef> workingFiles) {
        if (workingFiles == null || workingFiles.isEmpty()) {
            return List.of();
        }

        Set<WorkspaceItemRef> unique = new LinkedHashSet<>();
        workingFiles.stream()
                .filter(item -> item != null && item.path() != null && !item.path().isBlank())
                .forEach(item -> unique.add(new WorkspaceItemRef(
                        item.path(),
                        item.label() == null || item.label().isBlank() ? item.path() : item.label(),
                        "file".equals(item.kind()) ? "file" : "folder"
                )));
        return List.copyOf(unique);
    }

    private boolean isWorkspaceOrganizationIntent(String taskIntent) {
        return StringUtils.hasText(taskIntent)
                && WORKSPACE_ORGANIZE_INTENT.equalsIgnoreCase(taskIntent.trim());
    }

    private String resolveTaskIntent(String explicitTaskIntent, String prompt) {
        if (isWorkspaceOrganizationIntent(explicitTaskIntent)) {
            return WORKSPACE_ORGANIZE_INTENT;
        }

        if (!StringUtils.hasText(prompt)) {
            return explicitTaskIntent;
        }

        String normalizedPrompt = prompt.trim().toLowerCase(Locale.ROOT);
        boolean hasAction = WORKSPACE_ORGANIZE_ACTION_HINTS.stream()
                .map(hint -> hint.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedPrompt::contains);
        boolean hasTarget = WORKSPACE_ORGANIZE_TARGET_HINTS.stream()
                .map(hint -> hint.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedPrompt::contains);

        if (hasAction && hasTarget) {
            return WORKSPACE_ORGANIZE_INTENT;
        }

        return explicitTaskIntent;
    }

    private String serializeWorkingFiles(List<WorkspaceItemRef> workingFiles) {
        try {
            return objectMapper.writeValueAsString(workingFiles == null ? List.of() : workingFiles);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to persist workbench scope.");
        }
    }

    private List<WorkspaceItemRef> deserializeWorkingFiles(String workingFilesJson) {
        if (workingFilesJson == null || workingFilesJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(workingFilesJson, WORKSPACE_ITEM_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to read workbench scope.");
        }
    }

    private String serializePlan(WorkbenchPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to persist workbench plan.");
        }
    }

    private WorkbenchPlan deserializePlan(String planJson) {
        if (!StringUtils.hasText(planJson) || "{}".equals(planJson.trim())) {
            return null;
        }

        try {
            return objectMapper.readValue(planJson, WORKBENCH_PLAN_TYPE);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private WorkbenchRunRuntime deserializeRuntime(String runtimeJson) {
        if (!StringUtils.hasText(runtimeJson) || "{}".equals(runtimeJson.trim())) {
            return null;
        }

        try {
            return objectMapper.readValue(runtimeJson, WorkbenchRunRuntime.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private WorkbenchRunStatus resolveStatus(String executionMode) {
        return "prepare".equalsIgnoreCase(executionMode)
                ? WorkbenchRunStatus.PREPARED
                : WorkbenchRunStatus.RUNNING;
    }

    private String resolveInspectMode(String executionMode, String planInspectMode) {
        if ("execute".equalsIgnoreCase(executionMode)) {
            return StringUtils.hasText(planInspectMode) ? planInspectMode : "full";
        }
        return "focus";
    }

    private String normalizePrompt(String prompt, List<WorkspaceItemRef> workingFiles, boolean globalScope) {
        String normalized = prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ");
        if (!normalized.isBlank()) {
            return normalized;
        }
        if (!workingFiles.isEmpty()) {
            return "整理当前工作文件，并生成下一步工作草稿。";
        }
        if (globalScope) {
            return "扫描全局工作区，规划下一步资料整理链路。";
        }
        return "检查当前工作区范围，并规划下一步。";
    }

    private String buildRunName(String rawPrompt, List<WorkspaceItemRef> workingFiles) {
        String normalizedPrompt = rawPrompt == null ? "" : rawPrompt.trim().replaceAll("\\s+", " ");
        if (!normalizedPrompt.isBlank()) {
            return truncate(normalizedPrompt, 42);
        }
        if (!workingFiles.isEmpty()) {
            return truncate("资料任务 - " + workingFiles.getFirst().label(), 42);
        }
        return "资料任务 - " + TIME_FORMATTER.format(LocalDateTime.now());
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
