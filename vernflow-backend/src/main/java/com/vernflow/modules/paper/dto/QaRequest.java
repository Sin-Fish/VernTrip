package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.QaScope;

import jakarta.validation.constraints.NotBlank;

public record QaRequest(
        Long sessionId,
        Long paperId,
        QaScope scope,
        @NotBlank String question
) {
}
