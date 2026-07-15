package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_relation")
public class PaperRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_paper_id", nullable = false)
    private Paper sourcePaper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_paper_id", nullable = false)
    private Paper targetPaper;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private RelationType relationType;

    @Column(nullable = false)
    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    protected PaperRelation() {
    }

    private PaperRelation(Paper sourcePaper, Paper targetPaper, RelationType relationType, Double confidence, String evidence) {
        this.sourcePaper = sourcePaper;
        this.targetPaper = targetPaper;
        this.relationType = relationType;
        this.confidence = confidence;
        this.evidence = evidence;
    }

    public static PaperRelation create(Paper sourcePaper, Paper targetPaper, RelationType relationType, Double confidence, String evidence) {
        return new PaperRelation(sourcePaper, targetPaper, relationType, confidence, evidence);
    }

    public Long getId() {
        return id;
    }

    public Paper getSourcePaper() {
        return sourcePaper;
    }

    public Paper getTargetPaper() {
        return targetPaper;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getEvidence() {
        return evidence;
    }
}

