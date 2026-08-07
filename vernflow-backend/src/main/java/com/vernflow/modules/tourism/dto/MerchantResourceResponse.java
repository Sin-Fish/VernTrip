package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MerchantResourceResponse(
        Long id,
        String city,
        String name,
        String category,
        String categoryLabel,
        String district,
        String summary,
        List<String> tags,
        String sourceName,
        String sourceUrl,
        String openHours,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String status,
        String statusLabel,
        int qualityScore,
        LocalDateTime updatedAt
) {
}
