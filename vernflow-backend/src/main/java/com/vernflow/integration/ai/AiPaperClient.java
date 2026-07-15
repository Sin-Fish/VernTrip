package com.vernflow.integration.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.paper.domain.QaScope;
import com.vernflow.modules.workbench.dto.WorkbenchDecisionRequest;
import com.vernflow.modules.workbench.dto.WorkbenchDecisionResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/**
 * Client wrapper around rag-side document/rag APIs and agent-side planning APIs.
 */
@Component
public class AiPaperClient {

    private final RestClient ragRestClient;
    private final RestClient agentRestClient;

    public AiPaperClient(
            @Qualifier("ragServiceRestClient") RestClient ragRestClient,
            @Qualifier("agentServiceRestClient") RestClient agentRestClient
    ) {
        this.ragRestClient = ragRestClient;
        this.agentRestClient = agentRestClient;
    }

    public ParsePdfResponse parsePaper(String filePath) {
        try {
            ParsePdfResponse response = ragRestClient.post()
                    .uri("/ai/parse-pdf")
                    .body(new ParsePdfRequest(filePath))
                    .retrieve()
                    .body(ParsePdfResponse.class);
            return Objects.requireNonNull(response, "AI parse service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI parse service: " + exception.getMessage());
        }
    }

    public RagQueryResponse askQuestion(String question, QaScope scope, List<RagChunk> chunks) {
        return askQuestion(question, scope.name(), chunks);
    }

    public RagQueryResponse askQuestion(String question, String scope, List<RagChunk> chunks) {
        return askQuestion(question, scope, chunks, null, List.of());
    }

    public RagQueryResponse askQuestion(String question, String scope, List<RagChunk> chunks, String profileId) {
        return askQuestion(question, scope, chunks, profileId, List.of());
    }

    public RagQueryResponse askQuestion(
            String question,
            String scope,
            List<RagChunk> chunks,
            String profileId,
            List<ConversationMessage> conversationContext
    ) {
        try {
            RagQueryResponse response = ragRestClient.post()
                    .uri("/ai/rag-query")
                    .body(new RagQueryRequest(question, scope, chunks, profileId, conversationContext))
                    .retrieve()
                    .body(RagQueryResponse.class);
            return Objects.requireNonNull(response, "AI question-answer service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI question-answer service: " + exception.getMessage());
        }
    }

    public RagRetrieveResponse retrieveEvidence(String question, List<RagChunk> chunks) {
        return retrieveEvidence(question, chunks, null);
    }

    public RagRetrieveResponse retrieveEvidence(String question, List<RagChunk> chunks, String profileId) {
        try {
            RagRetrieveResponse response = ragRestClient.post()
                    .uri("/ai/rag-retrieve")
                    .body(new RagRetrieveRequest(question, chunks, profileId))
                    .retrieve()
                    .body(RagRetrieveResponse.class);
            return Objects.requireNonNull(response, "AI retrieval service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI retrieval service: " + exception.getMessage());
        }
    }

    public RagQueryResponse rerankAndAnswer(
            String question,
            String scope,
            List<RagScoredChunk> candidates,
            String profileId
    ) {
        return rerankAndAnswer(question, scope, candidates, profileId, List.of());
    }

    public RagQueryResponse rerankAndAnswer(
            String question,
            String scope,
            List<RagScoredChunk> candidates,
            String profileId,
            List<ConversationMessage> conversationContext
    ) {
        try {
            RagQueryResponse response = ragRestClient.post()
                    .uri("/ai/rag-rerank-query")
                    .body(new RagRerankQueryRequest(question, scope, candidates, profileId, conversationContext))
                    .retrieve()
                    .body(RagQueryResponse.class);
            return Objects.requireNonNull(response, "AI rerank question-answer service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI rerank question-answer service: " + exception.getMessage());
        }
    }

    public RagRetrieveResponse rerankEvidence(
            String question,
            List<RagScoredChunk> candidates,
            String profileId
    ) {
        try {
            RagRetrieveResponse response = ragRestClient.post()
                    .uri("/ai/rag-rerank-retrieve")
                    .body(new RagRerankRetrieveRequest(question, candidates, profileId))
                    .retrieve()
                    .body(RagRetrieveResponse.class);
            return Objects.requireNonNull(response, "AI rerank retrieval service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI rerank retrieval service: " + exception.getMessage());
        }
    }

    public EmbedTextsResponse embedTexts(List<String> texts) {
        return embedTexts(texts, "passage");
    }

    public EmbedTextsResponse embedTexts(List<String> texts, String inputType) {
        try {
            EmbedTextsResponse response = ragRestClient.post()
                    .uri("/ai/embed-texts")
                    .body(new EmbedTextsRequest(texts, inputType))
                    .retrieve()
                    .body(EmbedTextsResponse.class);
            return Objects.requireNonNull(response, "AI embedding service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI embedding service: " + exception.getMessage());
        }
    }

    public WorkbenchDecisionResponse planWorkbench(WorkbenchDecisionRequest request) {
        try {
            WorkbenchDecisionResponse response = agentRestClient.post()
                    .uri("/ai/plan-workbench")
                    .body(request)
                    .retrieve()
                    .body(WorkbenchDecisionResponse.class);
            return Objects.requireNonNull(response, "AI planning service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI planning service: " + exception.getMessage());
        }
    }

    private record ParsePdfRequest(@JsonProperty("file_path") String filePath) {
    }

    public record ParsePdfResponse(
            String title,
            List<String> authors,
            @JsonProperty("publication_year") Integer publicationYear,
            String venue,
            @JsonProperty("abstract_text") String abstractText,
            @JsonProperty("ai_summary") String aiSummary,
            @JsonProperty("summary_problem") String summaryProblem,
            @JsonProperty("summary_method") String summaryMethod,
            @JsonProperty("summary_conclusion") String summaryConclusion,
            @JsonProperty("summary_value") String summaryValue,
            List<String> keywords,
            List<ReferenceItem> references,
            List<ChunkItem> chunks
    ) {
        public ParsePdfResponse {
            authors = authors == null ? List.of() : authors;
            keywords = keywords == null ? List.of() : keywords;
            references = references == null ? List.of() : references;
            chunks = chunks == null ? List.of() : chunks;
        }
    }

    public record ReferenceItem(
            @JsonProperty("raw_reference") String rawReference,
            @JsonProperty("ref_title") String refTitle,
            @JsonProperty("ref_authors") String refAuthors,
            @JsonProperty("ref_year") Integer refYear
    ) {
    }

    public record ChunkItem(
            @JsonProperty("section_title") String sectionTitle,
            @JsonProperty("page_no") Integer pageNo,
            @JsonProperty("chunk_text") String chunkText,
            @JsonProperty("embedding_id") String embeddingId
    ) {
    }

    private record RagQueryRequest(
            String question,
            String scope,
            List<RagChunk> chunks,
            @JsonProperty("profile_id") String profileId,
            @JsonProperty("conversation_context") List<ConversationMessage> conversationContext
    ) {
        private RagQueryRequest {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
            conversationContext = conversationContext == null ? List.of() : List.copyOf(conversationContext);
        }
    }

    private record RagRetrieveRequest(
            String question,
            List<RagChunk> chunks,
            @JsonProperty("profile_id") String profileId
    ) {
    }

    private record RagRerankQueryRequest(
            String question,
            String scope,
            List<RagScoredChunk> candidates,
            @JsonProperty("profile_id") String profileId,
            @JsonProperty("conversation_context") List<ConversationMessage> conversationContext
    ) {
        private RagRerankQueryRequest {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            conversationContext = conversationContext == null ? List.of() : List.copyOf(conversationContext);
        }
    }

    private record RagRerankRetrieveRequest(
            String question,
            List<RagScoredChunk> candidates,
            @JsonProperty("profile_id") String profileId
    ) {
    }

    private record EmbedTextsRequest(
            List<String> texts,
            @JsonProperty("input_type") String inputType
    ) {
    }

    public record RagChunk(
            @JsonProperty("paper_id") Long paperId,
            @JsonProperty("paper_title") String paperTitle,
            @JsonProperty("section_title") String sectionTitle,
            @JsonProperty("page_no") Integer pageNo,
            @JsonProperty("chunk_text") String chunkText
    ) {
    }

    public record RagScoredChunk(
            @JsonProperty("paper_id") Long paperId,
            @JsonProperty("paper_title") String paperTitle,
            @JsonProperty("section_title") String sectionTitle,
            @JsonProperty("page_no") Integer pageNo,
            @JsonProperty("chunk_text") String chunkText,
            double score
    ) {
    }

    public record RagQueryResponse(String answer, List<RagEvidence> evidences, RagProfileMetadata profile) {
        public RagQueryResponse {
            evidences = evidences == null ? List.of() : evidences;
        }

        public RagQueryResponse(String answer, List<RagEvidence> evidences) {
            this(answer, evidences, null);
        }
    }

    public record ConversationMessage(String role, String text) {
    }

    public record RagRetrieveResponse(List<RagEvidence> evidences, RagProfileMetadata profile) {
        public RagRetrieveResponse {
            evidences = evidences == null ? List.of() : evidences;
        }

        public RagRetrieveResponse(List<RagEvidence> evidences) {
            this(evidences, null);
        }
    }

    public record RagProfileMetadata(
            @JsonProperty("profile_id") String profileId,
            @JsonProperty("retriever_id") String retrieverId,
            @JsonProperty("selector_id") String selectorId,
            @JsonProperty("graph_strategy_id") String graphStrategyId,
            @JsonProperty("generator_id") String generatorId
    ) {
    }

    public record EmbedTextsResponse(List<EmbeddingItem> embeddings) {
        public EmbedTextsResponse {
            embeddings = embeddings == null ? List.of() : embeddings;
        }
    }

    public record EmbeddingItem(
            @JsonProperty("embedding_id") String embeddingId,
            List<Float> vector
    ) {
        public EmbeddingItem {
            vector = vector == null ? List.of() : vector;
        }
    }

    public record RagEvidence(
            @JsonProperty("paper_id") Long paperId,
            @JsonProperty("paper_title") String paperTitle,
            @JsonProperty("section_title") String sectionTitle,
            @JsonProperty("page_no") Integer pageNo,
            String excerpt,
            double score
    ) {
    }
}
