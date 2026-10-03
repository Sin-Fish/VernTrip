package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.util.List;

public record TourismResourceResponse(
        Long id,
        String type,
        String name,
        String area,
        String placeName,
        String locationStatus,
        String address,
        String amapPoiId,
        BigDecimal longitude,
        BigDecimal latitude,
        String note,
        String source,
        String sourceUrl,
        String openHours,
        BigDecimal priceMin,
        BigDecimal priceMax,
        boolean reservationRequired,
        Integer availableCapacity,
        String availabilityStatus,
        List<String> tags,
        String videoUrl
) {
}
