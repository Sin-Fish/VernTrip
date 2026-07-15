package com.vernflow.modules.workbench.dto;

import java.util.List;

/**
 * Structured planning request sent to the local AI service for workbench graph generation.
 *
 * @param prompt normalized user task
 * @param scopeMode current scope mode label
 * @param workingFileCount number of selected working files
 * @param totalScopeFileCount total files visible to the planner
 * @param readableDocumentCount readable documents extracted from the scope
 * @param workingFileLabels labels of the current working files
 * @param unreadableFiles files that could not be converted into readable text
 * @param documents short document previews used for planning
 * @param availableTools registered tool options visible to the planner
 */
public record WorkbenchDecisionRequest(
        String prompt,
        String scopeMode,
        int workingFileCount,
        long totalScopeFileCount,
        long readableDocumentCount,
        List<String> workingFileLabels,
        List<String> unreadableFiles,
        List<DocumentPreview> documents,
        List<ToolOption> availableTools
) {

    public WorkbenchDecisionRequest {
        workingFileLabels = workingFileLabels == null ? List.of() : List.copyOf(workingFileLabels);
        unreadableFiles = unreadableFiles == null ? List.of() : List.copyOf(unreadableFiles);
        documents = documents == null ? List.of() : List.copyOf(documents);
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }

    public record DocumentPreview(
            String path,
            String citeLabel,
            String sectionTitle,
            int chunkCount,
            String excerpt
    ) {
    }

    public record ToolOption(
            String id,
            String name,
            String summary,
            String type,
            String positionClass,
            boolean branchTool,
            boolean readWorkspace,
            boolean writeWorkspace,
            boolean useNetwork
    ) {
    }
}
