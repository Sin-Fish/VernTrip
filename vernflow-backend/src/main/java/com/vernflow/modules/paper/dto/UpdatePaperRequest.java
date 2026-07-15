package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.util.List;

public record UpdatePaperRequest(
        String title,
        String authors,
        Integer publicationYear,
        String venue,
        String abstractText,
        String aiSummary,
        String summaryProblem,
        String summaryMethod,
        String summaryConclusion,
        String summaryValue,
        List<String> keywords
) {
}

