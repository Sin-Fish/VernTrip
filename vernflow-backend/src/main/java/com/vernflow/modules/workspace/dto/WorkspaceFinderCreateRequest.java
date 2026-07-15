package com.vernflow.modules.workspace.dto;

/**
 * Create file/folder request.
 *
 * @param path target directory path
 * @param name desired name
 */
public record WorkspaceFinderCreateRequest(String path, String name) {
}
