package com.vernflow.modules.workbench.model;

import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.util.List;

/**
 * Archived page snapshot inside a persistent workbench run.
 */
public record WorkbenchRunHistoryPage(
        String name,
        String skills,
        String status,
        String statusTone,
        String inspectMode,
        String planSummary,
        String prompt,
        boolean globalScope,
        List<WorkspaceItemRef> workingFiles,
        String selectedNodeId,
        String activeExecutionNodeId,
        String activeInspectionNodeId,
        List<WorkspaceBootstrapResponse.WorkbenchNode> nodes,
        List<WorkspaceBootstrapResponse.WorkbenchLink> graphLinks,
        List<String> graphMainPath,
        String time
) {
}
