package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "qa_session")
public class QaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private QaScope scope;

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "show_evidence", nullable = false)
    private boolean showEvidence = true;

    @Column(name = "show_sources", nullable = false)
    private boolean showSources = true;

    @Column(name = "show_page_numbers", nullable = false)
    private boolean showPageNumbers = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<QaEvidence> evidences = new ArrayList<>();

    protected QaSession() {
    }

    private QaSession(Long userId, Paper paper, String question, QaScope scope) {
        this.userId = userId;
        this.paper = paper;
        this.title = question;
        this.question = question;
        this.scope = scope;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static QaSession create(Long userId, Paper paper, String question, QaScope scope) {
        return new QaSession(userId, paper, question, scope);
    }

    public void complete(String answer) {
        this.answer = answer;
        touch();
    }

    public void syncWorkbenchState(
            String title,
            String summary,
            QaScope scope,
            Paper paper,
            Boolean archived,
            Boolean showEvidence,
            Boolean showSources,
            Boolean showPageNumbers,
            String messagesJson,
            String latestQuestion,
            String latestAnswer
    ) {
        if (title != null) {
            this.title = title;
        }
        this.summary = summary;
        if (scope != null) {
            this.scope = scope;
        }
        this.paper = paper;
        if (archived != null) {
            this.archived = archived;
        }
        if (showEvidence != null) {
            this.showEvidence = showEvidence;
        }
        if (showSources != null) {
            this.showSources = showSources;
        }
        if (showPageNumbers != null) {
            this.showPageNumbers = showPageNumbers;
        }
        this.messagesJson = messagesJson;
        if (latestQuestion != null) {
            this.question = latestQuestion;
        }
        this.answer = latestAnswer;
        touch();
    }

    public void replaceEvidences(List<QaEvidence> nextEvidences) {
        this.evidences.clear();
        nextEvidences.forEach(this::addEvidence);
        touch();
    }

    public void addEvidence(QaEvidence evidence) {
        evidence.attachTo(this);
        this.evidences.add(evidence);
        touch();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Paper getPaper() {
        return paper;
    }

    public String getQuestion() {
        return question;
    }

    public String getTitle() {
        return title;
    }

    public String getAnswer() {
        return answer;
    }

    public String getSummary() {
        return summary;
    }

    public QaScope getScope() {
        return scope;
    }

    public String getMessagesJson() {
        return messagesJson;
    }

    public boolean isArchived() {
        return archived;
    }

    public boolean isShowEvidence() {
        return showEvidence;
    }

    public boolean isShowSources() {
        return showSources;
    }

    public boolean isShowPageNumbers() {
        return showPageNumbers;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<QaEvidence> getEvidences() {
        return evidences;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

