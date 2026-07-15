package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.time.LocalDateTime;
import java.util.List;

public record PaperItemResponse(
        Long id,
        String title,
        String authors,
        Integer publicationYear,
        String venue,
        String aiSummary,
        List<String> keywords,
        ParseStatus parseStatus,
        LocalDateTime createdAt
) {
}

