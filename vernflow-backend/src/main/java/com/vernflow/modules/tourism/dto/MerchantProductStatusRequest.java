package com.vernflow.modules.tourism.dto;

import com.vernflow.modules.tourism.domain.TourismProduct;

import jakarta.validation.constraints.NotNull;

public record MerchantProductStatusRequest(
        @NotNull TourismProduct.Status status
) {
}
