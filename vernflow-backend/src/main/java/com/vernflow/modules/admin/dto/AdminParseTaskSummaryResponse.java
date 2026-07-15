package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminParseTaskSummaryResponse(
        Long id,
        Long paperId,
        String paperTitle,
        String taskType,
        String status,
        long queueOrder,
        LocalDateTime updatedAt
) {
}
