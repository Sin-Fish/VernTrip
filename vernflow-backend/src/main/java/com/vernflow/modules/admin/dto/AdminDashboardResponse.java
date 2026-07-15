package com.vernflow.modules.admin.dto;

import java.util.List;

public record AdminDashboardResponse(
        long workspaceFileCount,
        long workspaceFolderCount,
        long assistantSessionCount,
        long workbenchRunCount,
        long parseTaskCount,
        long adminLogCount,
        List<AdminRecentActivityResponse> recentActivities
) {
}
