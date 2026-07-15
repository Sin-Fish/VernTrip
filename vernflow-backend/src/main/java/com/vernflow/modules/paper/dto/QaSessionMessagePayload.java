package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.QaScope;

import java.util.List;

public record QaSessionMessagePayload(
        String id,
        String role,
        String content,
        String createdAt,
        QaScope scope,
        List<Evidence> evidences
) {

    public record Evidence(
            Long paperId,
            String paperTitle,
            String sectionTitle,
            Integer pageNo,
            String excerpt,
            Double score
    ) {
    }
}
