package com.vernflow.modules.admin.dto;

import java.util.List;

public record AdminStorageListResponse(
        String currentPath,
        String parentPath,
        List<AdminStorageEntryResponse> items
) {
}
