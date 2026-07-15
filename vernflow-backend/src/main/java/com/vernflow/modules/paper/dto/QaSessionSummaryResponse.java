package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.QaScope;

import java.time.LocalDateTime;

public record QaSessionSummaryResponse(
        Long id,
        Long paperId,
        String paperTitle,
        String title,
        String question,
        QaScope scope,
        String summary,
        boolean archived,
        int messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
