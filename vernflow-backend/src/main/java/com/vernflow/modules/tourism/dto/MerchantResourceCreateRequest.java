package com.vernflow.modules.tourism.dto;

import com.vernflow.modules.tourism.domain.TourismResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

public record MerchantResourceCreateRequest(
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 160) String name,
        @NotNull TourismResource.Category category,
        @NotBlank @Size(max = 80) String district,
        @NotBlank @Size(max = 160) String placeName,
        @NotBlank @Size(max = 64) String amapPoiId,
        @Size(max = 255) String address,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Size(max = 1200) String summary,
        List<@Size(max = 40) String> tags,
        @Size(max = 160) String openHours,
        @DecimalMin("0") BigDecimal priceMin,
        @DecimalMin("0") BigDecimal priceMax,
        Boolean reservationRequired,
        @Min(0) Integer dailyCapacity,
        @Min(0) Integer availableCapacity
) {
}
