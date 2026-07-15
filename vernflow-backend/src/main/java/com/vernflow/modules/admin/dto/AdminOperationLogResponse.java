package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminOperationLogResponse(
        Long id,
        String actor,
        String action,
        String targetType,
        String targetPath,
        String detail,
        LocalDateTime createdAt
) {
}
