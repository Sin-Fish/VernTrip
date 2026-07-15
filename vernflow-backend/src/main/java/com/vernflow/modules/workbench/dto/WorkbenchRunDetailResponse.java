package com.vernflow.modules.workbench.dto;

import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.util.List;

/**
 * Detail payload for a persisted workbench run.
 *
 * @param id run id
 * @param name run title
 * @param skills enabled skill summary
 * @param status current status label
 * @param statusTone status tone used by the frontend list
 * @param inspectMode inspection mode label
 * @param planSummary short planning summary
 * @param prompt task prompt
 * @param globalScope whether the full workspace was enabled
 * @param workingFiles selected workspace references captured by the run
 * @param selectedNodeId default highlighted node for this run
 * @param activeExecutionNodeId current execution-agent cursor
 * @param activeInspectionNodeId current inspection-agent cursor
 * @param nodes graph nodes
 * @param graphLinks graph edges
 * @param graphMainPath highlighted main path
 * @param currentPage currently displayed page number inside the run
 * @param totalPages total page count inside the run
 * @param time display time label
 */
public record WorkbenchRunDetailResponse(
        Long id,
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
        int currentPage,
        int totalPages,
        String time
) {
}
