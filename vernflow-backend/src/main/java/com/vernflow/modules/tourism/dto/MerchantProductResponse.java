package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MerchantProductResponse(
        Long id,
        String name,
        String targetAudience,
        int durationDays,
        BigDecimal price,
        String description,
        List<String> highlights,
        List<ResourceSummary> resources,
        String status,
        String statusLabel,
        LocalDateTime updatedAt
) {

    public record ResourceSummary(
            Long id,
            String name,
            String category,
            String categoryLabel,
            String status,
            boolean available
    ) {
    }
}
