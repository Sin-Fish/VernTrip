package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

public record PaperReferenceResponse(
        Long id,
        String rawReference,
        String refTitle,
        String refAuthors,
        Integer refYear,
        Long matchedPaperId,
        String matchedPaperTitle,
        Double matchScore
) {
}

