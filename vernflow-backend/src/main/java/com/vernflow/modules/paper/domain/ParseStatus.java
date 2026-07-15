package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

public enum ParseStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}

