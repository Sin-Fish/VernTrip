package com.vernflow.modules.tourism.dto;

import com.vernflow.modules.tourism.domain.TourismResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MerchantResourceCreateRequest(
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 160) String name,
        @NotNull TourismResource.Category category,
        @NotBlank @Size(max = 80) String district,
        @Size(max = 1200) String summary,
        List<@Size(max = 40) String> tags
) {
}
