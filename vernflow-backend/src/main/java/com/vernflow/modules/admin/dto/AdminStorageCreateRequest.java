package com.vernflow.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminStorageCreateRequest(
        String parentPath,
        @NotBlank String name
) {
}
