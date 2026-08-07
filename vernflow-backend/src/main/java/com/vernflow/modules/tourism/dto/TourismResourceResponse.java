package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.util.List;

public record TourismResourceResponse(
        Long id,
        String type,
        String name,
        String area,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String note,
        String source,
        String sourceUrl,
        BigDecimal priceMin,
        BigDecimal priceMax,
        List<String> tags
) {
}
