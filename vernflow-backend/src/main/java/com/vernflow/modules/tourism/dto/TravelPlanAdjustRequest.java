package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TravelPlanAdjustRequest(
        @NotBlank String type,
        @Size(max = 1000) String note
) {
}
