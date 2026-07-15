package com.vernflow.modules.assistant.dto;

import java.time.LocalDateTime;

/**
 * Assistant session card shown in the session list.
 *
 * @param id session id
 * @param title session title
 * @param summary latest concise summary
 * @param messageCount number of stored messages
 * @param updatedAt last update time
 */
public record AssistantSessionSummaryResponse(
        Long id,
        String title,
        String summary,
        int messageCount,
        LocalDateTime updatedAt
) {
}
