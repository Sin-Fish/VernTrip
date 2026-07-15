package com.vernflow.modules.workbench.service;

import com.vernflow.modules.workbench.domain.WorkbenchRun;
import com.vernflow.modules.workbench.domain.WorkbenchRunStatus;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;
import com.vernflow.modules.workbench.model.WorkbenchPlan;
import com.vernflow.modules.workbench.model.WorkbenchRunHistoryPage;
import com.vernflow.modules.workbench.model.WorkbenchRunRuntime;
import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkbenchRunPresentationService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public WorkbenchRunDetailResponse toDetail(
            WorkbenchRun run,
            WorkbenchPlan plan,
            List<WorkspaceItemRef> workingFiles,
            WorkbenchRunRuntime runtime,
            List<WorkbenchRunHistoryPage> historyPages,
            Integer requestedPage,
            String defaultSelectedNodeId
    ) {
        int totalPages = historyPages.size() + 1;
        int resolvedPage = requestedPage == null
                ? totalPages
                : Math.max(1, Math.min(requestedPage, totalPages));

        if (resolvedPage < totalPages) {
            return toDetail(historyPages.get(resolvedPage - 1), run.getId(), resolvedPage, totalPages);
        }

        return new WorkbenchRunDetailResponse(
                run.getId(),
                run.getName(),
                StringUtils.hasText(run.getToolChain()) ? run.getToolChain() : plan.toolChain(),
                statusLabel(run.getStatus()),
                statusTone(run.getStatus()),
                run.getInspectMode(),
                StringUtils.hasText(run.getPlanSummary()) ? run.getPlanSummary() : plan.summary(),
                run.getPrompt(),
                run.isGlobalScope(),
                workingFiles,
                defaultSelectedNodeId,
                runtime.getActiveExecutionNodeId(),
                runtime.getActiveInspectionNodeId(),
                buildNodes(plan, runtime),
                buildLinks(plan),
                plan.mainPath(),
                totalPages,
                totalPages,
                formatTime(run.getUpdatedAt())
        );
    }

    public WorkbenchRunDetailResponse toDetail(
            WorkbenchRunHistoryPage page,
            Long runId,
            int currentPage,
            int totalPages
    ) {
        return new WorkbenchRunDetailResponse(
                runId,
                page.name(),
                page.skills(),
                page.status(),
                page.statusTone(),
                page.inspectMode(),
                page.planSummary(),
                page.prompt(),
                page.globalScope(),
                page.workingFiles(),
                page.selectedNodeId(),
                page.activeExecutionNodeId(),
                page.activeInspectionNodeId(),
                page.nodes(),
                page.graphLinks(),
                page.graphMainPath(),
                currentPage,
                totalPages,
                page.time()
        );
    }

    public WorkspaceBootstrapResponse.WorkbenchRecord toRecord(WorkbenchRun run, WorkbenchPlan plan) {
        return new WorkspaceBootstrapResponse.WorkbenchRecord(
                run.getId(),
                run.getName(),
                StringUtils.hasText(run.getToolChain()) ? run.getToolChain() : plan.toolChain(),
                statusLabel(run.getStatus()),
                statusTone(run.getStatus()),
                run.getInspectMode(),
                formatTime(run.getUpdatedAt())
        );
    }

    private List<WorkspaceBootstrapResponse.WorkbenchNode> buildNodes(WorkbenchPlan plan, WorkbenchRunRuntime runtime) {
        return plan.nodes().stream()
                .map(node -> {
                    WorkbenchRunRuntime.NodeState runtimeNode = runtime.findNode(node.id());
                    NodeStatusPresentation presentation = presentNodeStatus(runtimeNode, runtime, node.id());
                    return new WorkspaceBootstrapResponse.WorkbenchNode(
                            node.id(),
                            node.type(),
                            node.title(),
                            presentation.status(),
                            presentation.tone(),
                            resolveNodeDetail(node, runtimeNode, presentation),
                            node.positionClass(),
                            presentation.executeStatus(),
                            presentation.executeTone(),
                            presentation.inspectStatus(),
                            presentation.inspectTone(),
                            presentation.executionActive(),
                            presentation.inspectionActive(),
                            buildChecks(node, runtimeNode, presentation)
                    );
                })
                .toList();
    }

    private List<WorkspaceBootstrapResponse.WorkbenchLink> buildLinks(WorkbenchPlan plan) {
        return plan.links().stream()
                .map(link -> new WorkspaceBootstrapResponse.WorkbenchLink(link.from(), link.to(), link.dashed()))
                .toList();
    }

    private List<WorkspaceBootstrapResponse.NodeCheck> buildChecks(
            WorkbenchPlan.Node node,
            WorkbenchRunRuntime.NodeState runtimeNode,
            NodeStatusPresentation presentation
    ) {
        List<WorkspaceBootstrapResponse.NodeCheck> checks = new ArrayList<>(node.checks().stream()
                .map(check -> new WorkspaceBootstrapResponse.NodeCheck(check.tone(), check.label(), check.result()))
                .toList());

        checks.add(new WorkspaceBootstrapResponse.NodeCheck(
                presentation.executeTone(),
                "执行智能体",
                presentation.executeResult()
        ));
        checks.add(new WorkspaceBootstrapResponse.NodeCheck(
                presentation.inspectTone(),
                "检查智能体",
                presentation.inspectResult()
        ));

        if (runtimeNode != null) {
            runtimeNode.getChecks().stream()
                    .map(check -> new WorkspaceBootstrapResponse.NodeCheck(
                            check.getTone(),
                            check.getLabel(),
                            check.getResult()
                    ))
                    .forEach(checks::add);

            runtimeNode.getIssues().forEach(issue -> checks.add(
                    new WorkspaceBootstrapResponse.NodeCheck("warn", "待复核", issue)
            ));
        }

        return List.copyOf(checks);
    }

    private String resolveNodeDetail(
            WorkbenchPlan.Node node,
            WorkbenchRunRuntime.NodeState runtimeNode,
            NodeStatusPresentation presentation
    ) {
        if ("audit".equals(node.id())) {
            return presentation.inspectResult();
        }
        if (runtimeNode != null
                && StringUtils.hasText(runtimeNode.getExecuteResult())
                && !"pending".equals(runtimeNode.getExecuteStatus())) {
            return runtimeNode.getExecuteResult();
        }
        return node.detail();
    }

    private NodeStatusPresentation presentNodeStatus(
            WorkbenchRunRuntime.NodeState runtimeNode,
            WorkbenchRunRuntime runtime,
            String nodeId
    ) {
        String executeStatus = runtimeNode == null ? "pending" : runtimeNode.getExecuteStatus();
        String inspectStatus = runtimeNode == null ? "pending" : runtimeNode.getInspectStatus();
        boolean executionActive = nodeId.equals(runtime.getActiveExecutionNodeId());
        boolean inspectionActive = nodeId.equals(runtime.getActiveInspectionNodeId());

        String aggregateStatus;
        String aggregateTone;
        if (executionActive) {
            aggregateStatus = "executing";
            aggregateTone = "run";
        } else if (inspectionActive) {
            aggregateStatus = "inspecting";
            aggregateTone = "run";
        } else if ("failed".equals(executeStatus) || "failed".equals(inspectStatus)) {
            aggregateStatus = "failed";
            aggregateTone = "warn";
        } else if ("warn".equals(inspectStatus)) {
            aggregateStatus = "warn";
            aggregateTone = "warn";
        } else if ("completed".equals(executeStatus) && "passed".equals(inspectStatus)) {
            aggregateStatus = "done";
            aggregateTone = "done";
        } else if ("completed".equals(executeStatus) && "pending".equals(inspectStatus)) {
            aggregateStatus = "queued";
            aggregateTone = "wait";
        } else if ("completed".equals(executeStatus)) {
            aggregateStatus = "done";
            aggregateTone = "done";
        } else {
            aggregateStatus = "prepared";
            aggregateTone = "wait";
        }

        return new NodeStatusPresentation(
                aggregateStatus,
                aggregateTone,
                executeStatus,
                toneForExecute(executeStatus),
                inspectionStatusLabel(inspectStatus),
                toneForInspect(inspectStatus),
                runtimeNode == null ? "等待执行" : runtimeNode.getExecuteResult(),
                runtimeNode == null ? "等待检查" : runtimeNode.getInspectResult(),
                executionActive,
                inspectionActive
        );
    }

    private String statusLabel(WorkbenchRunStatus status) {
        return switch (status) {
            case PREPARED -> "Prepared";
            case RUNNING -> "Running";
            case COMPLETED -> "Completed";
        };
    }

    private String statusTone(WorkbenchRunStatus status) {
        return switch (status) {
            case PREPARED -> "warn";
            case RUNNING -> "run";
            case COMPLETED -> "done";
        };
    }

    private String toneForExecute(String status) {
        return switch (status) {
            case "completed" -> "on";
            case "running" -> "run";
            case "failed" -> "warn";
            case "skipped" -> "wait";
            default -> "wait";
        };
    }

    private String toneForInspect(String status) {
        return switch (status) {
            case "passed" -> "on";
            case "running" -> "run";
            case "warn", "failed" -> "warn";
            case "skipped" -> "wait";
            default -> "wait";
        };
    }

    private String inspectionStatusLabel(String status) {
        return switch (status) {
            case "passed" -> "passed";
            case "warn" -> "warn";
            case "failed" -> "failed";
            case "running" -> "running";
            case "skipped" -> "skipped";
            default -> "pending";
        };
    }

    private String formatTime(LocalDateTime time) {
        return TIME_FORMATTER.format(time);
    }

    private record NodeStatusPresentation(
            String status,
            String tone,
            String executeStatus,
            String executeTone,
            String inspectStatus,
            String inspectTone,
            String executeResult,
            String inspectResult,
            boolean executionActive,
            boolean inspectionActive
    ) {
    }
}
