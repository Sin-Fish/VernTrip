package com.vernflow.modules.tourism.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MerchantResourceAuditResponse(
        int currentScore,
        int projectedScore,
        boolean recommendable,
        String generatedSummary,
        List<String> generatedTags,
        List<AuditCheck> checks,
        List<String> suggestions,
        String recommendationRationale,
        String engine,
        LocalDateTime generatedAt
) {

    public record AuditCheck(
            String code,
            String label,
            String status,
            String detail
    ) {
    }
}
