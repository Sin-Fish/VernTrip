package com.vernflow.modules.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AdminStorageDeleteRequest(@NotEmpty List<String> paths) {
}
