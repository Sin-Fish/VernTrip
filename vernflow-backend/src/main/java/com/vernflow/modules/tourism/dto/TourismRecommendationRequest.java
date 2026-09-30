package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TourismRecommendationRequest(
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Pattern(regexp = "culture|slow|family") String profile,
        @NotBlank @Size(max = 40) String travelers,
        @NotBlank @Size(max = 40) String pace,
        @NotNull @DecimalMin("0") @DecimalMax("200000") BigDecimal budget,
        @NotEmpty @Size(max = 12) List<@NotNull @Positive Long> candidateResourceIds
) {
}
