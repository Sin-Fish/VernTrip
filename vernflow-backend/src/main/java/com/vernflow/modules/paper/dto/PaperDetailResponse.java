package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.time.LocalDateTime;
import java.util.List;

public record PaperDetailResponse(
        Long id,
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
        List<String> keywords,
        ParseStatus parseStatus,
        String originalFileName,
        String filePath,
        int chunkCount,
        long outboundCitationCount,
        long inboundCitationCount,
        List<PaperReferenceResponse> references,
        ParseTaskResponse latestTask,
        LocalDateTime updatedAt
) {
}

