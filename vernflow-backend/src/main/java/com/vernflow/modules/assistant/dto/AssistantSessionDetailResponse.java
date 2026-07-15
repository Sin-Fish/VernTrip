package com.vernflow.modules.assistant.dto;

import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full assistant session detail used by the frontend conversation view.
 *
 * @param id session id
 * @param title session title
 * @param summary latest concise summary
 * @param globalScope whether the session searched the full workspace
 * @param workingFiles captured working-file scope
 * @param createdAt creation time
 * @param updatedAt last update time
 * @param messages message timeline
 */
public record AssistantSessionDetailResponse(
        Long id,
        String title,
        String summary,
        boolean globalScope,
        List<WorkspaceItemRef> workingFiles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AssistantMessagePayload> messages
) {
}
