package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TravelDraft(
        @Size(max = 40) String destination,
        @Size(max = 40) String departure,
        @Size(max = 20) String date,
        @Min(1) @Max(7) Integer days,
        @Size(max = 40) String travelers,
        @DecimalMin("0.00") BigDecimal budget,
        @Size(max = 40) String pace,
        @Size(max = 12) List<@Size(max = 40) String> interests,
        @Size(max = 1000) String notes
) {
}
