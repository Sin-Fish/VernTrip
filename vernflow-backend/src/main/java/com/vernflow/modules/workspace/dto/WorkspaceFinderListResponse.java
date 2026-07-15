package com.vernflow.modules.workspace.dto;

import java.util.List;

/**
 * File-system shaped payload consumed by VueFinder.
 *
 * @param storages available storages
 * @param dirname current directory path
 * @param files files and folders in the current directory
 * @param read_only whether the current directory is read-only
 */
public record WorkspaceFinderListResponse(
        List<String> storages,
        String dirname,
        List<Entry> files,
        boolean read_only
) {

    /**
     * VueFinder directory entry.
     *
     * @param dir parent directory path
     * @param basename visible item name
     * @param extension file extension for files
     * @param path unique item path
     * @param storage storage id
     * @param type file or dir
     * @param file_size file size in bytes
     * @param last_modified last modified timestamp in epoch millis
     * @param mime_type mime type for files
     * @param read_only whether the entry is read-only
     * @param visibility visibility label
     * @param previewUrl optional direct preview url
     */
    public record Entry(
            String dir,
            String basename,
            String extension,
            String path,
            String storage,
            String type,
            Long file_size,
            Long last_modified,
            String mime_type,
            boolean read_only,
            String visibility,
            String previewUrl
    ) {
    }
}
