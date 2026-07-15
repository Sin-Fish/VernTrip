package com.vernflow.modules.workspace.dto;

import java.util.List;

/**
 * Delete request payload matching VueFinder.
 *
 * @param path current directory path
 * @param items selected items
 */
public record WorkspaceFinderDeleteRequest(String path, List<ItemRef> items) {

    /**
     * Selected item reference.
     *
     * @param path item path
     * @param type file or dir
     */
    public record ItemRef(String path, String type) {
    }
}
