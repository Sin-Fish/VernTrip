package com.vernflow.modules.workspace.dto;

/**
 * Lightweight workspace item reference shared by the workspace shell.
 *
 * @param path workspace virtual path
 * @param label display label
 * @param kind file or folder
 */
public record WorkspaceItemRef(String path, String label, String kind) {
}
