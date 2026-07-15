package com.vernflow.modules.assistant.dto;

import java.util.List;

/**
 * Stored assistant timeline message.
 *
 * @param id message id
 * @param role user or assistant
 * @param text rendered message body
 * @param createdAt ISO timestamp
 * @param cites cited file labels or paths
 */
public record AssistantMessagePayload(
        String id,
        String role,
        String text,
        String createdAt,
        List<String> cites
) {
}
