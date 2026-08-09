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
        Long placeId,
        String placeName,
        String locationStatus,
        String amapPoiId,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String summary,
        List<String> tags,
        String sourceName,
        String sourceUrl,
        String openHours,
        BigDecimal priceMin,
        BigDecimal priceMax,
        boolean reservationRequired,
        Integer dailyCapacity,
        Integer availableCapacity,
        String availabilityStatus,
        String availabilityLabel,
        String status,
        String statusLabel,
        int qualityScore,
        LocalDateTime updatedAt
) {
}
