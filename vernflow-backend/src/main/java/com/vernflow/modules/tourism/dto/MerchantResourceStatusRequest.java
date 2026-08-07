package com.vernflow.modules.tourism.dto;

import com.vernflow.modules.tourism.domain.TourismResource;

import jakarta.validation.constraints.NotNull;

public record MerchantResourceStatusRequest(
        @NotNull TourismResource.PublicationStatus status
) {
}
