package com.vernflow.modules.workbench.dto;

import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.util.List;

/**
 * Create a new workspace-first workbench run.
 *
 * @param prompt current task prompt
 * @param workingFiles selected workspace references
 * @param globalScope whether the whole workspace is enabled
 * @param executionMode "prepare" keeps a saved task shell, "execute" materializes a completed run
 * @param taskIntent optional explicit task intent such as workspace organization
 * @param generateExecutionReport whether to append a report-writing step after organization
 */
public record WorkbenchRunCreateRequest(
        String prompt,
        List<WorkspaceItemRef> workingFiles,
        Boolean globalScope,
        String executionMode,
        String taskIntent,
        Boolean generateExecutionReport
) {
}
