package com.vernflow.modules.admin.dto;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String displayName,
        long workspaceFileCount,
        long workspaceFolderCount,
        long assistantSessionCount,
        long workbenchRunCount
) {
}
