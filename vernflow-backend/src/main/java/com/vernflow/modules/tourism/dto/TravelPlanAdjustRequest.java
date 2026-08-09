package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TravelPlanAdjustRequest(
        @NotBlank String type,
        @Size(max = 1000) String note,
        String action,
        Long itemId,
        @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "时间格式必须为 HH:mm") String time
) {
}
