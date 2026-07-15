package com.vernflow.modules.admin.dto;

public record AdminStorageEntryResponse(
        String name,
        String path,
        String kind,
        Long size,
        Long modifiedAt
) {
}
