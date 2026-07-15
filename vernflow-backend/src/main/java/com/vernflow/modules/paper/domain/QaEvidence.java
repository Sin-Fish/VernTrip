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
@Table(name = "qa_evidence")
public class QaEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private QaSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    private PaperChunk chunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @Column(name = "message_id", length = 120)
    private String messageId;

    @Column(name = "relation_path", columnDefinition = "TEXT")
    private String relationPath;

    private Double score;

    @Column(name = "quote_text", columnDefinition = "TEXT")
    private String quoteText;

    protected QaEvidence() {
    }

    private QaEvidence(PaperChunk chunk, Paper paper, String messageId, String relationPath, Double score, String quoteText) {
        this.chunk = chunk;
        this.paper = paper;
        this.messageId = messageId;
        this.relationPath = relationPath;
        this.score = score;
        this.quoteText = quoteText;
    }

    public static QaEvidence create(PaperChunk chunk, Paper paper, String relationPath, Double score, String quoteText) {
        return new QaEvidence(chunk, paper, null, relationPath, score, quoteText);
    }

    public static QaEvidence create(PaperChunk chunk, Paper paper, String messageId, String relationPath, Double score, String quoteText) {
        return new QaEvidence(chunk, paper, messageId, relationPath, score, quoteText);
    }

    public void attachTo(QaSession session) {
        this.session = session;
    }

    public Long getId() {
        return id;
    }

    public QaSession getSession() {
        return session;
    }

    public PaperChunk getChunk() {
        return chunk;
    }

    public Paper getPaper() {
        return paper;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRelationPath() {
        return relationPath;
    }

    public Double getScore() {
        return score;
    }

    public String getQuoteText() {
        return quoteText;
    }
}

