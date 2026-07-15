package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.util.List;

public record QaResponse(Long sessionId, String answer, QaScope scope, List<Evidence> evidences) {

    public record Evidence(
            Long paperId,
            String paperTitle,
            String sectionTitle,
            Integer pageNo,
            String excerpt,
            double score
    ) {
    }
}

