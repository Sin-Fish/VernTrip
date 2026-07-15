package com.vernflow.modules.workspace.dto;

import org.springframework.http.MediaType;

import java.nio.file.Path;

/**
 * Descriptor used by preview and download endpoints.
 *
 * @param originalFileName display file name
 * @param filePath absolute local file path
 * @param mediaType resolved media type
 */
public record WorkspaceFileDescriptor(String originalFileName, Path filePath, MediaType mediaType) {
}
