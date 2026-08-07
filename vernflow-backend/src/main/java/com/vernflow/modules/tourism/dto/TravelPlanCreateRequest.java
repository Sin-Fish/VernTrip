package com.vernflow.modules.tourism.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TravelPlanCreateRequest(
        @NotBlank String destination,
        String departure,
        @NotNull LocalDate date,
        @NotNull @Min(1) @Max(2) Integer days,
        @NotBlank String travelers,
        @NotNull @DecimalMin("1200.00") BigDecimal budget,
        @NotBlank String pace,
        @NotEmpty List<@Valid @NotBlank String> interests,
        @Size(max = 1000) String notes
) {
}
