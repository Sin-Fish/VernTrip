package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminWorkbenchRunSummaryResponse(
        Long id,
        String name,
        String status,
        String inspectMode,
        boolean globalScope,
        int workingFileCount,
        LocalDateTime createdAt
) {
}
