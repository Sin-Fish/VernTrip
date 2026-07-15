package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Local storage configuration.
 *
 * @param paperUploadDir legacy paper upload directory
 * @param workspaceRootDir generic workspace file root
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Path paperUploadDir, Path workspaceRootDir) {
}
