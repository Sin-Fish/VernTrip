package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.time.LocalDateTime;

public record ExternalPaperResponse(
        Long id,
        String title,
        String authors,
        Integer year,
        String abstractText,
        String url,
        String source,
        ExternalPaperStatus status,
        LocalDateTime createdAt
) {
}

