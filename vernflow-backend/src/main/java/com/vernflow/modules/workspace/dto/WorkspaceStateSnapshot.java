package com.vernflow.modules.workspace.dto;

import java.util.List;

/**
 * Persisted workspace-shell state.
 *
 * @param workingFiles selected working files or folders
 * @param globalScope whether the workspace is in global mode
 * @param selectedTreePath selected item in the right-side explorer
 * @param homeTaskDraft homepage quick-task draft
 * @param workbenchPrompt workbench prompt draft
 */
public record WorkspaceStateSnapshot(
        List<WorkspaceItemRef> workingFiles,
        boolean globalScope,
        String selectedTreePath,
        String homeTaskDraft,
        String workbenchPrompt
) {
}
