package com.vernflow.modules.openclaw.service;

import com.vernflow.modules.assistant.service.WorkspaceDocumentScopeService;
import com.vernflow.modules.openclaw.dto.OpenClawWorkspaceScopeResponse;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenClawWorkspaceToolService {

    private static final int MAX_DOCUMENTS = 12;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 4;
    private static final int MAX_CHUNK_CHARACTERS = 700;
    private static final int MAX_UNREADABLE_FILES = 12;

    private final OpenClawSessionContextService contextService;
    private final WorkspaceDocumentScopeService workspaceDocumentScopeService;

    public OpenClawWorkspaceToolService(
            OpenClawSessionContextService contextService,
            WorkspaceDocumentScopeService workspaceDocumentScopeService
    ) {
        this.contextService = contextService;
        this.workspaceDocumentScopeService = workspaceDocumentScopeService;
    }

    public OpenClawWorkspaceScopeResponse readScope(String sessionKey) {
        OpenClawSessionContextService.ResolvedSession context = contextService.resolveActive(sessionKey);
        WorkspaceDocumentScopeService.ScopeExtractionResult scope = workspaceDocumentScopeService.collectScope(
                context.userId(),
                context.globalScope(),
                context.workingFiles()
        );

        List<OpenClawWorkspaceScopeResponse.Document> documents = scope.readableDocuments().stream()
                .limit(MAX_DOCUMENTS)
                .map(document -> new OpenClawWorkspaceScopeResponse.Document(
                        document.path(),
                        document.citeLabel(),
                        document.sectionTitle(),
                        document.chunks().stream()
                                .limit(MAX_CHUNKS_PER_DOCUMENT)
                                .map(this::truncateChunk)
                                .toList()
                ))
                .toList();

        return new OpenClawWorkspaceScopeResponse(
                context.globalScope(),
                scope.totalScopeFileCount(),
                documents,
                scope.unreadableFiles().stream().limit(MAX_UNREADABLE_FILES).toList()
        );
    }

    private String truncateChunk(String chunk) {
        return chunk.length() <= MAX_CHUNK_CHARACTERS
                ? chunk
                : chunk.substring(0, MAX_CHUNK_CHARACTERS);
    }
}
