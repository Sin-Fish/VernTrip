package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.QaScope;

import java.time.LocalDateTime;
import java.util.List;

public record QaSessionDetailResponse(
        Long id,
        Long paperId,
        String paperTitle,
        String title,
        String summary,
        String question,
        String answer,
        QaScope scope,
        boolean archived,
        boolean showEvidence,
        boolean showSources,
        boolean showPageNumbers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<QaSessionMessagePayload> messages,
        List<Evidence> evidences
) {

    public record Evidence(
            Long id,
            Long paperId,
            String paperTitle,
            Long chunkId,
            String sectionTitle,
            Integer pageNo,
            String relationPath,
            String quoteText,
            Double score
    ) {
    }
}
