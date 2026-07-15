package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminRecentActivityResponse(
        String type,
        String title,
        String detail,
        LocalDateTime createdAt
) {
}
