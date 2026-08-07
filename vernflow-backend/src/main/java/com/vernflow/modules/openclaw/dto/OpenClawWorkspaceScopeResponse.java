package com.vernflow.modules.openclaw.dto;

import java.util.List;

public record OpenClawWorkspaceScopeResponse(
        boolean globalScope,
        long totalScopeFileCount,
        List<Document> documents,
        List<String> unreadableFiles
) {

    public record Document(
            String path,
            String citeLabel,
            String sectionTitle,
            List<String> chunks
    ) {
    }
}
