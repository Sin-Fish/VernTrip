package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record MerchantProductUpsertRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 120) String targetAudience,
        @Min(1) @Max(14) int durationDays,
        @NotNull @DecimalMin("0") BigDecimal price,
        @NotBlank @Size(max = 3000) String description,
        @Size(max = 8) List<@NotBlank @Size(max = 40) String> highlights,
        @NotEmpty @Size(max = 12) List<@NotNull Long> resourceIds
) {
}
