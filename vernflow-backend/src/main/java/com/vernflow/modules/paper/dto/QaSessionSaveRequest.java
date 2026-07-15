package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.QaScope;

import java.util.List;

public record QaSessionSaveRequest(
        Long id,
        Long paperId,
        String title,
        String summary,
        QaScope scope,
        Boolean archived,
        Boolean showEvidence,
        Boolean showSources,
        Boolean showPageNumbers,
        List<QaSessionMessagePayload> messages
) {
}
