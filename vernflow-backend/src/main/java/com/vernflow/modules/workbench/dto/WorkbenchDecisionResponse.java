package com.vernflow.modules.workbench.dto;

import java.util.List;

/**
 * Structured planner output returned by the local AI service.
 *
 * @param title proposed run title
 * @param summary short planning summary
 * @param inspectMode suggested inspection mode
 * @param mainTools ordered main-path tool ids
 * @param branchTools optional off-path tool ids
 * @param nodeNotes optional per-tool title/detail/rationale overrides
 */
public record WorkbenchDecisionResponse(
        String title,
        String summary,
        String inspectMode,
        List<String> mainTools,
        List<String> branchTools,
        List<NodeNote> nodeNotes
) {

    public WorkbenchDecisionResponse {
        mainTools = mainTools == null ? List.of() : List.copyOf(mainTools);
        branchTools = branchTools == null ? List.of() : List.copyOf(branchTools);
        nodeNotes = nodeNotes == null ? List.of() : List.copyOf(nodeNotes);
    }

    public record NodeNote(
            String toolId,
            String title,
            String detail,
            String rationale
    ) {
    }
}
