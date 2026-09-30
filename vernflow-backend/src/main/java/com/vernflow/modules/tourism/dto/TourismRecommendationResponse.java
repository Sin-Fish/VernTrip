package com.vernflow.modules.tourism.dto;

import java.util.List;

public record TourismRecommendationResponse(
        String engine,
        List<RecommendationItem> recommendations
) {

    public record RecommendationItem(
            Long resourceId,
            int matchScore,
            String reason,
            String caution
    ) {
    }
}
