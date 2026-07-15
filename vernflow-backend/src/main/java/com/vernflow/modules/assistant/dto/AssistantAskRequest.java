package com.vernflow.modules.assistant.dto;

import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import java.util.List;

/**
 * Ask the workspace assistant using the current scope snapshot.
 *
 * @param sessionId existing session id or null for a new thread
 * @param prompt user question
 * @param globalScope whether the assistant can search the whole workspace
 * @param workingFiles selected working-file scope
 */
public record AssistantAskRequest(
        Long sessionId,
        String prompt,
        boolean globalScope,
        List<WorkspaceItemRef> workingFiles
) {
}
