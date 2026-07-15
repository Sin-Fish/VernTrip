package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import com.vernflow.common.util.TextNormalizer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "paper")
public class Paper {

    private static final int TITLE_MAX_LENGTH = 300;
    private static final int AUTHORS_MAX_LENGTH = 500;
    private static final int VENUE_MAX_LENGTH = 255;
    private static final int KEYWORDS_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 500)
    private String authors;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(length = 255)
    private String venue;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "summary_problem", columnDefinition = "TEXT")
    private String summaryProblem;

    @Column(name = "summary_method", columnDefinition = "TEXT")
    private String summaryMethod;

    @Column(name = "summary_conclusion", columnDefinition = "TEXT")
    private String summaryConclusion;

    @Column(name = "summary_value", columnDefinition = "TEXT")
    private String summaryValue;

    @Column(length = 500)
    private String keywords;

    @Column(name = "original_file_name", nullable = false, length = 260)
    private String originalFileName;

    @Column(name = "file_path", nullable = false, unique = true, length = 1024)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private ParseStatus parseStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PaperChunk> chunks = new ArrayList<>();

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PaperReference> references = new ArrayList<>();

    protected Paper() {
    }

    private Paper(String originalFileName, String filePath) {
        this.title = trimToLength(deriveTitleFromFileName(originalFileName), TITLE_MAX_LENGTH);
        this.authors = "";
        this.originalFileName = originalFileName;
        this.filePath = filePath;
        this.parseStatus = ParseStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Paper create(String originalFileName, String filePath) {
        return new Paper(originalFileName, filePath);
    }

    public void markQueued() {
        this.parseStatus = ParseStatus.PENDING;
        touch();
    }

    public void markProcessing() {
        this.parseStatus = ParseStatus.PROCESSING;
        touch();
    }

    public void markReady() {
        this.parseStatus = ParseStatus.READY;
        touch();
    }

    public void markFailed() {
        this.parseStatus = ParseStatus.FAILED;
        touch();
    }

    public void applyParseResult(AiPaperClient.ParsePdfResponse parseResult) {
        if (StringUtils.hasText(parseResult.title())) {
            this.title = trimToLength(parseResult.title(), TITLE_MAX_LENGTH);
        }
        this.authors = defaultIfNull(trimToLength(String.join(", ", parseResult.authors()), AUTHORS_MAX_LENGTH), "");
        this.publicationYear = parseResult.publicationYear();
        this.venue = blankToNull(parseResult.venue(), VENUE_MAX_LENGTH);
        this.abstractText = blankToNull(parseResult.abstractText());
        this.aiSummary = blankToNull(parseResult.aiSummary());
        this.summaryProblem = blankToNull(parseResult.summaryProblem());
        this.summaryMethod = blankToNull(parseResult.summaryMethod());
        this.summaryConclusion = blankToNull(parseResult.summaryConclusion());
        this.summaryValue = blankToNull(parseResult.summaryValue());
        this.keywords = trimToLength(TextNormalizer.joinKeywords(parseResult.keywords()), KEYWORDS_MAX_LENGTH);
        markReady();
    }

    public void updateMetadata(UpdatePaperRequest request) {
        if (StringUtils.hasText(request.title())) {
            this.title = trimToLength(request.title(), TITLE_MAX_LENGTH);
        }
        if (request.authors() != null) {
            this.authors = defaultIfNull(trimToLength(request.authors(), AUTHORS_MAX_LENGTH), "");
        }
        if (request.publicationYear() != null) {
            this.publicationYear = request.publicationYear();
        }
        if (request.venue() != null) {
            this.venue = blankToNull(request.venue(), VENUE_MAX_LENGTH);
        }
        if (request.abstractText() != null) {
            this.abstractText = blankToNull(request.abstractText());
        }
        if (request.aiSummary() != null) {
            this.aiSummary = blankToNull(request.aiSummary());
        }
        if (request.summaryProblem() != null) {
            this.summaryProblem = blankToNull(request.summaryProblem());
        }
        if (request.summaryMethod() != null) {
            this.summaryMethod = blankToNull(request.summaryMethod());
        }
        if (request.summaryConclusion() != null) {
            this.summaryConclusion = blankToNull(request.summaryConclusion());
        }
        if (request.summaryValue() != null) {
            this.summaryValue = blankToNull(request.summaryValue());
        }
        if (request.keywords() != null) {
            this.keywords = trimToLength(TextNormalizer.joinKeywords(request.keywords()), KEYWORDS_MAX_LENGTH);
        }
        touch();
    }

    public void replaceChunks(List<PaperChunk> newChunks) {
        this.chunks.clear();
        newChunks.forEach(this::addChunk);
        touch();
    }

    public void replaceReferences(List<PaperReference> newReferences) {
        this.references.clear();
        newReferences.forEach(this::addReference);
        touch();
    }

    public void addChunk(PaperChunk chunk) {
        chunk.attachTo(this);
        this.chunks.add(chunk);
    }

    public void addReference(PaperReference reference) {
        reference.attachTo(this);
        this.references.add(reference);
    }

    public List<String> keywordList() {
        return TextNormalizer.splitKeywords(this.keywords);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthors() {
        return authors;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public String getVenue() {
        return venue;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public String getSummaryProblem() {
        return summaryProblem;
    }

    public String getSummaryMethod() {
        return summaryMethod;
    }

    public String getSummaryConclusion() {
        return summaryConclusion;
    }

    public String getSummaryValue() {
        return summaryValue;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<PaperChunk> getChunks() {
        return chunks;
    }

    public List<PaperReference> getReferences() {
        return references;
    }

    private static String deriveTitleFromFileName(String originalFileName) {
        Path file_name = Path.of(originalFileName);
        String source = file_name.getFileName().toString();
        int dot_index = source.lastIndexOf('.');
        String title = dot_index > 0 ? source.substring(0, dot_index) : source;
        return title.replace('_', ' ').trim();
    }

    private String blankToNull(String source) {
        return StringUtils.hasText(source) ? source.trim() : null;
    }

    private String blankToNull(String source, int maxLength) {
        return StringUtils.hasText(source) ? trimToLength(source, maxLength) : null;
    }

    private static String trimToLength(String source, int maxLength) {
        if (source == null) {
            return null;
        }
        String trimmed = source.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength).trim();
    }

    private static String defaultIfNull(String source, String fallback) {
        return source == null ? fallback : source;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

