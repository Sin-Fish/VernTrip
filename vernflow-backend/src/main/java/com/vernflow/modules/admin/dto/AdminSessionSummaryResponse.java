package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminSessionSummaryResponse(
        Long id,
        String title,
        String summary,
        int messageCount,
        boolean globalScope,
        int workingFileCount,
        LocalDateTime updatedAt
) {
}
