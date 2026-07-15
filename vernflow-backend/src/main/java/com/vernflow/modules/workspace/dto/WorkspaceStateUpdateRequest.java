package com.vernflow.modules.workspace.dto;

import java.util.List;

/**
 * Request payload for updating workspace-shell state.
 *
 * @param workingFiles selected working files or folders
 * @param globalScope whether the workspace is in global mode
 * @param selectedTreePath selected tree path
 * @param homeTaskDraft homepage quick-task draft
 * @param workbenchPrompt workbench prompt draft
 */
public record WorkspaceStateUpdateRequest(
        List<WorkspaceItemRef> workingFiles,
        Boolean globalScope,
        String selectedTreePath,
        String homeTaskDraft,
        String workbenchPrompt
) {
}
