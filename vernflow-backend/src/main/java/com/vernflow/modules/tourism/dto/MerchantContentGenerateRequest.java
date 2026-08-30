package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantContentGenerateRequest(
        @NotNull SourceType sourceType,
        @NotNull Long sourceId,
        @NotBlank @Size(max = 40) String channel,
        @NotBlank @Size(max = 40) String tone,
        @Size(max = 300) String goal
) {

    public enum SourceType {
        RESOURCE,
        PRODUCT
    }
}
