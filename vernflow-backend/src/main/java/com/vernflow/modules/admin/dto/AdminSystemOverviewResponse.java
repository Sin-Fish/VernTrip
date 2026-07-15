package com.vernflow.modules.admin.dto;

public record AdminSystemOverviewResponse(
        String adminUsername,
        String workspaceRootDir,
        String paperUploadDir,
        String aiBaseUrl,
        long aiTimeoutSeconds,
        String maxFileSize,
        String maxRequestSize
) {
}
