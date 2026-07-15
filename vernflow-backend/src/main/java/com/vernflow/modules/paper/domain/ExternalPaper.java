package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "external_paper")
public class ExternalPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String authors;

    @Column(name = "\"year\"")
    private Integer year;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExternalPaperStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ExternalPaper() {
    }

    private ExternalPaper(String title, String authors, Integer year, String abstractText, String url, String source) {
        this.title = title;
        this.authors = authors;
        this.year = year;
        this.abstractText = abstractText;
        this.url = url;
        this.source = source;
        this.status = ExternalPaperStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static ExternalPaper create(String title, String authors, Integer year, String abstractText, String url, String source) {
        return new ExternalPaper(title, authors, year, abstractText, url, source);
    }

    public void markImported() {
        this.status = ExternalPaperStatus.IMPORTED;
    }

    public void markIgnored() {
        this.status = ExternalPaperStatus.IGNORED;
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

    public Integer getYear() {
        return year;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public ExternalPaperStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

