package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import java.nio.file.Path;

public record PaperFileDescriptor(String originalFileName, Path filePath) {
}

