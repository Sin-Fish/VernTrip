package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.time.LocalDateTime;

public record ParseTaskResponse(
        Long id,
        Long paperId,
        String paperTitle,
        String paperOriginalFileName,
        TaskType taskType,
        TaskStatus status,
        long queueOrder,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}

