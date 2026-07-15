package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

public record AdminRelationResponse(
        Long id,
        Long sourcePaperId,
        String sourcePaperTitle,
        Long targetPaperId,
        String targetPaperTitle,
        RelationType relationType,
        Double confidence,
        String evidence
) {
}

