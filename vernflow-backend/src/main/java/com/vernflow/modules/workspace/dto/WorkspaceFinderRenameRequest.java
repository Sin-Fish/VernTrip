package com.vernflow.modules.workspace.dto;

/**
 * Rename request payload matching VueFinder.
 *
 * @param path current directory path
 * @param item selected item path
 * @param name new basename
 */
public record WorkspaceFinderRenameRequest(String path, String item, String name) {
}
