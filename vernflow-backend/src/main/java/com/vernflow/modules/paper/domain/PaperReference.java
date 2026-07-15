package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_reference")
public class PaperReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @Column(name = "raw_reference", nullable = false, columnDefinition = "TEXT")
    private String rawReference;

    @Column(name = "ref_title", length = 500)
    private String refTitle;

    @Column(name = "ref_authors", length = 500)
    private String refAuthors;

    @Column(name = "ref_year")
    private Integer refYear;

    @Column(name = "matched_paper_id")
    private Long matchedPaperId;

    @Column(name = "match_score")
    private Double matchScore;

    protected PaperReference() {
    }

    private PaperReference(String rawReference, String refTitle, String refAuthors, Integer refYear) {
        this.rawReference = rawReference;
        this.refTitle = refTitle;
        this.refAuthors = refAuthors;
        this.refYear = refYear;
    }

    public static PaperReference create(String rawReference, String refTitle, String refAuthors, Integer refYear) {
        return new PaperReference(rawReference, refTitle, refAuthors, refYear);
    }

    public void attachTo(Paper paper) {
        this.paper = paper;
    }

    public void updateMatch(Long matchedPaperId, Double matchScore) {
        this.matchedPaperId = matchedPaperId;
        this.matchScore = matchScore;
    }

    public Long getId() {
        return id;
    }

    public Paper getPaper() {
        return paper;
    }

    public String getRawReference() {
        return rawReference;
    }

    public String getRefTitle() {
        return refTitle;
    }

    public String getRefAuthors() {
        return refAuthors;
    }

    public Integer getRefYear() {
        return refYear;
    }

    public Long getMatchedPaperId() {
        return matchedPaperId;
    }

    public Double getMatchScore() {
        return matchScore;
    }
}

