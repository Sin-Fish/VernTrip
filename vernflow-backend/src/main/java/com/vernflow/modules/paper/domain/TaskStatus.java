package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

public enum TaskStatus {
    PENDING,
    PROCESSING,
    PAUSED,
    SUCCESS,
    FAILED
}

