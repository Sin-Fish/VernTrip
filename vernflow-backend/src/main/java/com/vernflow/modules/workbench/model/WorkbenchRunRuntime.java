package com.vernflow.modules.workbench.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable runtime snapshot for the execution and inspection agents that move along one plan graph.
 */
public class WorkbenchRunRuntime {

    private String activeExecutionNodeId;
    private String activeInspectionNodeId;
    private int executionCursor = -1;
    private int inspectionCursor = -1;
    private boolean executionFinished;
    private boolean inspectionFinished;
    private List<String> executionOrder = new ArrayList<>();
    private List<String> inspectionOrder = new ArrayList<>();
    private List<NodeState> nodes = new ArrayList<>();

    public WorkbenchRunRuntime() {
    }

    public String getActiveExecutionNodeId() {
        return activeExecutionNodeId;
    }

    public void setActiveExecutionNodeId(String activeExecutionNodeId) {
        this.activeExecutionNodeId = activeExecutionNodeId;
    }

    public String getActiveInspectionNodeId() {
        return activeInspectionNodeId;
    }

    public void setActiveInspectionNodeId(String activeInspectionNodeId) {
        this.activeInspectionNodeId = activeInspectionNodeId;
    }

    public int getExecutionCursor() {
        return executionCursor;
    }

    public void setExecutionCursor(int executionCursor) {
        this.executionCursor = executionCursor;
    }

    public int getInspectionCursor() {
        return inspectionCursor;
    }

    public void setInspectionCursor(int inspectionCursor) {
        this.inspectionCursor = inspectionCursor;
    }

    public boolean isExecutionFinished() {
        return executionFinished;
    }

    public void setExecutionFinished(boolean executionFinished) {
        this.executionFinished = executionFinished;
    }

    public boolean isInspectionFinished() {
        return inspectionFinished;
    }

    public void setInspectionFinished(boolean inspectionFinished) {
        this.inspectionFinished = inspectionFinished;
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(List<String> executionOrder) {
        this.executionOrder = executionOrder == null ? new ArrayList<>() : new ArrayList<>(executionOrder);
    }

    public List<String> getInspectionOrder() {
        return inspectionOrder;
    }

    public void setInspectionOrder(List<String> inspectionOrder) {
        this.inspectionOrder = inspectionOrder == null ? new ArrayList<>() : new ArrayList<>(inspectionOrder);
    }

    public List<NodeState> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeState> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    public NodeState findNode(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodes.stream()
                .filter(node -> nodeId.equals(node.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    public static class NodeState {

        private String nodeId;
        private String executeStatus = "pending";
        private String executeResult = "等待执行";
        private String inspectStatus = "pending";
        private String inspectResult = "等待检查";
        private List<String> issues = new ArrayList<>();
        private List<CheckState> checks = new ArrayList<>();

        public NodeState() {
        }

        public NodeState(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getExecuteStatus() {
            return executeStatus;
        }

        public void setExecuteStatus(String executeStatus) {
            this.executeStatus = executeStatus;
        }

        public String getExecuteResult() {
            return executeResult;
        }

        public void setExecuteResult(String executeResult) {
            this.executeResult = executeResult;
        }

        public String getInspectStatus() {
            return inspectStatus;
        }

        public void setInspectStatus(String inspectStatus) {
            this.inspectStatus = inspectStatus;
        }

        public String getInspectResult() {
            return inspectResult;
        }

        public void setInspectResult(String inspectResult) {
            this.inspectResult = inspectResult;
        }

        public List<String> getIssues() {
            return issues;
        }

        public void setIssues(List<String> issues) {
            this.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
        }

        public List<CheckState> getChecks() {
            return checks;
        }

        public void setChecks(List<CheckState> checks) {
            this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
        }
    }

    public static class CheckState {

        private String tone;
        private String label;
        private String result;

        public CheckState() {
        }

        public CheckState(String tone, String label, String result) {
            this.tone = tone;
            this.label = label;
            this.result = result;
        }

        public String getTone() {
            return tone;
        }

        public void setTone(String tone) {
            this.tone = tone;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
}
