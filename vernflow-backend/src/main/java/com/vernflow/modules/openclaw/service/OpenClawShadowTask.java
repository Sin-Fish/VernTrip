package com.vernflow.modules.openclaw.service;

import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.util.List;

public record OpenClawShadowTask(
        Long userId,
        Long assistantSessionId,
        String requestMessageId,
        String legacyMessageId,
        String prompt,
        boolean globalScope,
        List<WorkspaceItemRef> workingFiles
) {
}
