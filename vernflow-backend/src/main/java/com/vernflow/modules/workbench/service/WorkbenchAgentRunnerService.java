package com.vernflow.modules.workbench.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.workbench.domain.WorkbenchRun;
import com.vernflow.modules.workbench.domain.WorkbenchRunStatus;
import com.vernflow.modules.workbench.model.WorkbenchPlan;
import com.vernflow.modules.workbench.model.WorkbenchRunRuntime;
import com.vernflow.modules.workbench.repository.WorkbenchRunRepository;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared graph runner for both the execution agent and the inspection agent.
 */
@Service
public class WorkbenchAgentRunnerService {

    private static final long TICK_INTERVAL_MILLIS = 900L;
    private static final TypeReference<List<WorkspaceItemRef>> WORKSPACE_ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkbenchRunRepository workbenchRunRepository;
    private final ObjectMapper objectMapper;
    private final WorkbenchToolExecutionService workbenchToolExecutionService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Long, ScheduledFuture<?>> runningFutures = new ConcurrentHashMap<>();

    public WorkbenchAgentRunnerService(
            WorkbenchRunRepository workbenchRunRepository,
            ObjectMapper objectMapper,
            WorkbenchToolExecutionService workbenchToolExecutionService
    ) {
        this.workbenchRunRepository = workbenchRunRepository;
        this.objectMapper = objectMapper;
        this.workbenchToolExecutionService = workbenchToolExecutionService;
    }

    public WorkbenchRunRuntime initializeRuntime(WorkbenchPlan plan) {
        WorkbenchRunRuntime runtime = new WorkbenchRunRuntime();
        List<String> executionOrder = resolveExecutionOrder(plan);
        runtime.setExecutionOrder(executionOrder);
        runtime.setInspectionOrder(executionOrder);
        runtime.setNodes(plan.nodes().stream()
                .map(node -> new WorkbenchRunRuntime.NodeState(node.id()))
                .toList());
        return runtime;
    }

    public void restartRun(Long runId) {
        stopRun(runId);
        tickRun(runId);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> tickRun(runId),
                TICK_INTERVAL_MILLIS,
                TICK_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
        runningFutures.put(runId, future);
    }

    public void stopRun(Long runId) {
        ScheduledFuture<?> future = runningFutures.remove(runId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Transactional
    public void tickRun(Long runId) {
        WorkbenchRun run = workbenchRunRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() != WorkbenchRunStatus.RUNNING) {
            stopRun(runId);
            return;
        }

        WorkbenchPlan plan = deserializePlan(run.getPlanJson());
        WorkbenchRunRuntime runtime = deserializeRuntime(run.getRuntimeJson());
        if (plan == null || runtime == null) {
            stopRun(runId);
            return;
        }

        advanceExecution(run, runtime, plan);
        advanceInspection(runtime, plan);

        WorkbenchRunStatus nextStatus =
                runtime.isExecutionFinished() && runtime.isInspectionFinished()
                        ? WorkbenchRunStatus.COMPLETED
                        : WorkbenchRunStatus.RUNNING;

        run.revise(
                run.getName(),
                run.getPrompt(),
                run.isGlobalScope(),
                run.getInspectMode(),
                run.getToolChain(),
                run.getPlanSummary(),
                run.getPlanJson(),
                serializeRuntime(runtime),
                run.getHistoryJson(),
                run.getRagProfileId(),
                run.getWorkingFilesJson(),
                nextStatus
        );
        workbenchRunRepository.save(run);

        if (nextStatus == WorkbenchRunStatus.COMPLETED) {
            stopRun(runId);
        }
    }

    private void advanceExecution(WorkbenchRun run, WorkbenchRunRuntime runtime, WorkbenchPlan plan) {
        String activeNodeId = runtime.getActiveExecutionNodeId();
        if (activeNodeId != null) {
            WorkbenchRunRuntime.NodeState activeNode = runtime.findNode(activeNodeId);
            if (activeNode != null) {
                completeExecution(run, plan, activeNodeId, activeNode);
            }
            runtime.setActiveExecutionNodeId(null);
        }

        if (runtime.isExecutionFinished()) {
            return;
        }

        int nextIndex = runtime.getExecutionCursor() + 1;
        List<String> executionOrder = runtime.getExecutionOrder();
        if (nextIndex >= executionOrder.size()) {
            runtime.setExecutionFinished(true);
            return;
        }

        String nextNodeId = executionOrder.get(nextIndex);
        WorkbenchRunRuntime.NodeState nextNode = runtime.findNode(nextNodeId);
        if (nextNode != null) {
            nextNode.setExecuteStatus("running");
            nextNode.setExecuteResult(executionRunningMessage(plan, nextNodeId));
        }
        runtime.setExecutionCursor(nextIndex);
        runtime.setActiveExecutionNodeId(nextNodeId);
    }

    private void completeExecution(
            WorkbenchRun run,
            WorkbenchPlan plan,
            String activeNodeId,
            WorkbenchRunRuntime.NodeState activeNode
    ) {
        WorkbenchPlan.Node planNode = findPlanNode(plan, activeNodeId);
        WorkbenchToolExecutionService.NodeExecutionOutcome outcome = workbenchToolExecutionService.executeNode(
                run.getUserId(),
                run.getPrompt(),
                run.isGlobalScope(),
                deserializeWorkingFiles(run.getWorkingFilesJson()),
                planNode
        );

        activeNode.setExecuteStatus("completed");
        activeNode.setExecuteResult(
                StringUtils.hasText(outcome.executeResult())
                        ? outcome.executeResult()
                        : executionCompletedMessage(plan, activeNodeId)
        );
        activeNode.setChecks(outcome.handled() ? outcome.checks() : List.of());
        activeNode.setIssues(outcome.handled() ? outcome.issues() : List.of());
    }

    private void advanceInspection(WorkbenchRunRuntime runtime, WorkbenchPlan plan) {
        String activeNodeId = runtime.getActiveInspectionNodeId();
        if (activeNodeId != null) {
            WorkbenchRunRuntime.NodeState activeNode = runtime.findNode(activeNodeId);
            if (activeNode != null) {
                completeInspection(activeNode, plan, activeNodeId);
            }
            runtime.setActiveInspectionNodeId(null);
        }

        if (runtime.isInspectionFinished()) {
            return;
        }

        int executionLead = runtime.isExecutionFinished()
                ? runtime.getInspectionOrder().size() - 1
                : runtime.getExecutionCursor() - 1;

        int nextIndex = runtime.getInspectionCursor() + 1;
        if (nextIndex > executionLead || nextIndex < 0) {
            if (runtime.isExecutionFinished() && nextIndex >= runtime.getInspectionOrder().size()) {
                runtime.setInspectionFinished(true);
            }
            return;
        }

        List<String> inspectionOrder = runtime.getInspectionOrder();
        if (nextIndex >= inspectionOrder.size()) {
            runtime.setInspectionFinished(true);
            return;
        }

        String nextNodeId = inspectionOrder.get(nextIndex);
        WorkbenchRunRuntime.NodeState nextNode = runtime.findNode(nextNodeId);
        if (nextNode != null) {
            nextNode.setInspectStatus("running");
            nextNode.setInspectResult(inspectionRunningMessage(plan, nextNodeId));
        }
        runtime.setInspectionCursor(nextIndex);
        runtime.setActiveInspectionNodeId(nextNodeId);
    }

    private void completeInspection(WorkbenchRunRuntime.NodeState node, WorkbenchPlan plan, String nodeId) {
        String toolId = toolIdFor(plan, nodeId);
        List<String> issues = node.getIssues() == null ? List.of() : node.getIssues();
        boolean hasIssues = !issues.isEmpty();

        if (hasIssues) {
            node.setInspectStatus("warn");
            node.setInspectResult(inspectionWarnMessage(toolId, issues.size()));
            return;
        }

        node.setInspectStatus("passed");
        node.setInspectResult(inspectionPassedMessage(toolId));
    }

    private List<String> resolveExecutionOrder(WorkbenchPlan plan) {
        List<String> ordered = new ArrayList<>(plan.mainPath());
        boolean hasBranch = plan.nodes().stream().anyMatch(WorkbenchPlan.Node::branchNode);
        if (hasBranch && !ordered.contains("branch")) {
            int retrieverIndex = ordered.indexOf("retriever");
            if (retrieverIndex >= 0) {
                ordered.add(retrieverIndex + 1, "branch");
            } else {
                ordered.add("branch");
            }
        }
        return List.copyOf(ordered);
    }

    private String executionRunningMessage(WorkbenchPlan plan, String nodeId) {
        return switch (toolIdFor(plan, nodeId)) {
            case "scope-read" -> "正在锁定当前整理范围";
            case "local-retrieval" -> "正在召回关键证据";
            case "web-supplement" -> "正在补充外部公开资料";
            case "material-organize" -> "正在整理材料结构与要点";
            case "artifact-write" -> "正在生成结果草稿";
            case "claim-inspection" -> "正在汇总待检查结论";
            case "workspace-organize-plan" -> "正在规划资料分类与目录结构";
            case "workspace-move-files" -> "正在将资料移动到目标目录";
            case "workspace-folder-cleanup" -> "正在清理已经腾空的旧目录";
            case "workspace-review" -> "正在复核整理后的资料结构";
            case "workspace-write-report" -> "正在生成本次整理的执行报告";
            default -> "正在执行节点";
        };
    }

    private String executionCompletedMessage(WorkbenchPlan plan, String nodeId) {
        return switch (toolIdFor(plan, nodeId)) {
            case "scope-read" -> "已确认当前整理范围";
            case "local-retrieval" -> "关键证据已召回";
            case "web-supplement" -> "外部补充分支已合并";
            case "material-organize" -> "材料结构已整理";
            case "artifact-write" -> "结果草稿已生成";
            case "claim-inspection" -> "检查输入已就绪";
            case "workspace-organize-plan" -> "分类规划已生成";
            case "workspace-move-files" -> "文件迁移已完成";
            case "workspace-folder-cleanup" -> "空目录清理已完成";
            case "workspace-review" -> "整理复核已完成";
            case "workspace-write-report" -> "执行报告已生成";
            default -> "节点执行完成";
        };
    }

    private String inspectionRunningMessage(WorkbenchPlan plan, String nodeId) {
        return switch (toolIdFor(plan, nodeId)) {
            case "scope-read" -> "正在检查范围边界";
            case "local-retrieval" -> "正在检查证据覆盖";
            case "web-supplement" -> "正在复核外部来源";
            case "material-organize" -> "正在检查整理逻辑";
            case "artifact-write" -> "正在检查输出草稿";
            case "claim-inspection" -> "正在检查最终断言";
            case "workspace-organize-plan" -> "正在复核分类规划";
            case "workspace-move-files" -> "正在复核文件迁移结果";
            case "workspace-folder-cleanup" -> "正在确认空目录清理结果";
            case "workspace-review" -> "正在汇总整理复核结论";
            case "workspace-write-report" -> "正在检查执行报告是否写入成功";
            default -> "正在检查节点";
        };
    }

    private String inspectionWarnMessage(String toolId, int issueCount) {
        return switch (toolId) {
            case "scope-read" -> "范围边界仍有 " + issueCount + " 项待确认";
            case "local-retrieval" -> "证据召回仍有 " + issueCount + " 项待复核";
            case "web-supplement" -> "外部资料仍有 " + issueCount + " 项待确认";
            case "material-organize" -> "整理结果仍有 " + issueCount + " 项待复核";
            case "artifact-write" -> "输出草稿仍有 " + issueCount + " 项待复核";
            case "claim-inspection" -> "最终断言仍有 " + issueCount + " 项待复核";
            case "workspace-organize-plan" -> "分类规划仍有 " + issueCount + " 项待确认";
            case "workspace-move-files" -> "文件迁移仍有 " + issueCount + " 项待复核";
            case "workspace-folder-cleanup" -> "空目录清理仍有 " + issueCount + " 项待确认";
            case "workspace-review" -> "整理复核发现 " + issueCount + " 项待处理";
            case "workspace-write-report" -> "执行报告仍有 " + issueCount + " 项待复核";
            default -> "当前节点仍有 " + issueCount + " 项待复核";
        };
    }

    private String inspectionPassedMessage(String toolId) {
        return switch (toolId) {
            case "scope-read" -> "范围边界已确认";
            case "local-retrieval" -> "证据召回已复核";
            case "web-supplement" -> "外部来源已复核";
            case "material-organize" -> "整理逻辑已通过检查";
            case "artifact-write" -> "输出草稿已通过检查";
            case "claim-inspection" -> "最终断言已复核";
            case "workspace-organize-plan" -> "分类规划已确认";
            case "workspace-move-files" -> "文件迁移已确认";
            case "workspace-folder-cleanup" -> "空目录清理已确认";
            case "workspace-review" -> "整理结果已通过复核";
            case "workspace-write-report" -> "执行报告已通过检查";
            default -> "检查通过";
        };
    }

    private String toolIdFor(WorkbenchPlan plan, String nodeId) {
        return plan.nodes().stream()
                .filter(node -> nodeId.equals(node.id()))
                .map(WorkbenchPlan.Node::toolId)
                .findFirst()
                .orElse(nodeId);
    }

    private WorkbenchPlan.Node findPlanNode(WorkbenchPlan plan, String nodeId) {
        return plan.nodes().stream()
                .filter(node -> nodeId.equals(node.id()))
                .findFirst()
                .orElse(null);
    }

    private List<WorkspaceItemRef> deserializeWorkingFiles(String workingFilesJson) {
        if (!StringUtils.hasText(workingFilesJson)) {
            return List.of();
        }

        try {
            List<WorkspaceItemRef> items = objectMapper.readValue(workingFilesJson, WORKSPACE_ITEM_LIST_TYPE);
            return items == null ? List.of() : items;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private WorkbenchPlan deserializePlan(String planJson) {
        try {
            return objectMapper.readValue(planJson, WorkbenchPlan.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private WorkbenchRunRuntime deserializeRuntime(String runtimeJson) {
        try {
            return objectMapper.readValue(runtimeJson, WorkbenchRunRuntime.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    public String serializeRuntime(WorkbenchRunRuntime runtime) {
        try {
            return objectMapper.writeValueAsString(runtime);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist workbench runtime.", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        runningFutures.values().forEach(future -> future.cancel(false));
        runningFutures.clear();
        scheduler.shutdownNow();
    }
}
