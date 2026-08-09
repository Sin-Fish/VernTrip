package com.vernflow.modules.tourism.dto;

import com.vernflow.modules.tourism.domain.TourismResource;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record MerchantResourceUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull TourismResource.Category category,
        @NotBlank @Size(max = 1200) String summary,
        List<@Size(max = 40) String> tags,
        @Size(max = 160) String openHours,
        @NotNull @DecimalMin("0") BigDecimal priceMin,
        @NotNull @DecimalMin("0") BigDecimal priceMax,
        @NotNull Boolean reservationRequired,
        @Min(0) Integer dailyCapacity,
        @Min(0) Integer availableCapacity
) {
}
