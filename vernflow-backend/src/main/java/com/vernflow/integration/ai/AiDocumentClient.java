package com.vernflow.integration.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vernflow.common.exception.BusinessException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/**
 * Generic document-oriented AI client for workspace parsing flows.
 */
@Component
public class AiDocumentClient {

    private final RestClient restClient;

    public AiDocumentClient(@Qualifier("ragServiceRestClient") RestClient ragServiceRestClient) {
        this.restClient = ragServiceRestClient;
    }

    public ParseDocumentResponse parsePdf(String filePath) {
        try {
            ParseDocumentResponse response = restClient.post()
                    .uri("/ai/parse-pdf")
                    .body(new ParseDocumentRequest(filePath))
                    .retrieve()
                    .body(ParseDocumentResponse.class);
            return Objects.requireNonNull(response, "AI document parse service returned no response.");
        } catch (RestClientException exception) {
            throw new BusinessException("Failed to call AI document parse service: " + exception.getMessage());
        }
    }

    private record ParseDocumentRequest(@JsonProperty("file_path") String filePath) {
    }

    public record ParseDocumentResponse(
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
            List<DocumentReference> references,
            List<DocumentChunk> chunks
    ) {
        public ParseDocumentResponse {
            authors = authors == null ? List.of() : authors;
            keywords = keywords == null ? List.of() : keywords;
            references = references == null ? List.of() : references;
            chunks = chunks == null ? List.of() : chunks;
        }
    }

    public record DocumentReference(
            @JsonProperty("raw_reference") String rawReference,
            @JsonProperty("ref_title") String refTitle,
            @JsonProperty("ref_authors") String refAuthors,
            @JsonProperty("ref_year") Integer refYear
    ) {
    }

    public record DocumentChunk(
            @JsonProperty("section_title") String sectionTitle,
            @JsonProperty("page_no") Integer pageNo,
            @JsonProperty("chunk_text") String chunkText,
            @JsonProperty("embedding_id") String embeddingId
    ) {
    }
}
