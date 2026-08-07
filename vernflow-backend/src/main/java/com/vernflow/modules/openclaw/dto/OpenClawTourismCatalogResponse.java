package com.vernflow.modules.openclaw.dto;

import com.vernflow.modules.tourism.dto.TourismResourceResponse;

import java.util.List;

public record OpenClawTourismCatalogResponse(
        String city,
        boolean supported,
        int total,
        List<TourismResourceResponse> resources
) {
}
