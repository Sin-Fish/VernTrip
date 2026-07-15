package com.vernflow.modules.rag.service;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.assistant.service.WorkspaceDocumentScopeService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin backend-side facade for product RAG calls.
 * Backend owns scope extraction and permissions, while the rag service owns retrieval,
 * reranking, graph strategy, and answer generation.
 */
@Service
public class RagProfileFacade {

    private final AiPaperClient aiPaperClient;
    private final String defaultProfileId;

    public RagProfileFacade(
            AiPaperClient aiPaperClient,
            @Value("${app.rag.default-profile:tfidf_topk_basic}") String defaultProfileId
    ) {
        this.aiPaperClient = aiPaperClient;
        this.defaultProfileId = defaultProfileId;
    }

    public String defaultProfileId() {
        return defaultProfileId;
    }

    public AiPaperClient.RagQueryResponse query(
            String question,
            String scope,
            List<AiPaperClient.RagChunk> chunks
    ) {
        return query(question, scope, chunks, List.of());
    }

    public AiPaperClient.RagQueryResponse query(
            String question,
            String scope,
            List<AiPaperClient.RagChunk> chunks,
            List<AiPaperClient.ConversationMessage> conversationContext
    ) {
        return aiPaperClient.askQuestion(question, scope, chunks, defaultProfileId, conversationContext);
    }

    public AiPaperClient.RagQueryResponse queryFromScope(
            Long userId,
            String question,
            String scope,
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents,
            boolean pruneMissing
    ) {
        return queryFromScope(userId, question, scope, documents, pruneMissing, List.of());
    }

    public AiPaperClient.RagQueryResponse queryFromScope(
            Long userId,
            String question,
            String scope,
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents,
            boolean pruneMissing,
            List<AiPaperClient.ConversationMessage> conversationContext
    ) {
        return query(question, scope, materializeScopeChunks(documents), conversationContext);
    }

    public AiPaperClient.RagRetrieveResponse retrieve(
            String question,
            List<AiPaperClient.RagChunk> chunks
    ) {
        return aiPaperClient.retrieveEvidence(question, chunks, defaultProfileId);
    }

    public AiPaperClient.RagRetrieveResponse retrieveFromScope(
            Long userId,
            String question,
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents,
            boolean pruneMissing
    ) {
        return retrieve(question, materializeScopeChunks(documents));
    }

    public List<AiPaperClient.RagChunk> materializeScopeChunks(
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents
    ) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<AiPaperClient.RagChunk> chunks = new ArrayList<>();
        long paperId = 1L;
        for (WorkspaceDocumentScopeService.ReadableWorkspaceDocument document : documents) {
            if (document == null || document.chunks() == null || document.chunks().isEmpty()) {
                paperId++;
                continue;
            }

            String sectionTitle = document.sectionTitle();
            String paperTitle = document.citeLabel();
            for (String chunkText : document.chunks()) {
                chunks.add(new AiPaperClient.RagChunk(
                        paperId,
                        paperTitle,
                        sectionTitle,
                        null,
                        chunkText
                ));
            }
            paperId++;
        }
        return List.copyOf(chunks);
    }
}
