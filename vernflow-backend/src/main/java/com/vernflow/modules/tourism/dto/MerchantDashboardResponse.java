package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.util.List;

public record MerchantDashboardResponse(
        int resourceCount,
        int publishedResourceCount,
        int recommendableResourceCount,
        int averageQualityScore,
        int soldOutResourceCount,
        int limitedResourceCount,
        long productCount,
        long publishedProductCount,
        long plansLast7Days,
        long requestsLast30Days,
        BigDecimal averageBudget,
        double averageDays,
        List<InterestTrend> interestTrends,
        List<AudienceSegment> audienceSegments,
        List<ResourceCitation> resourceCitations,
        List<OperationTask> tasks,
        String operationAdvice
) {

    public record InterestTrend(String label, long count, int share) {
    }

    public record AudienceSegment(String label, long count, int share) {
    }

    public record ResourceCitation(Long resourceId, String resourceName, long count) {
    }

    public record OperationTask(String type, String title, String detail, String level, Long resourceId) {
    }
}
