package com.vernflow.modules.assistant.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persisted workspace assistant conversation.
 */
@Entity
@Table(name = "assistant_session")
public class AssistantSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "global_scope", nullable = false)
    private boolean globalScope;

    @Column(name = "working_files_json", columnDefinition = "TEXT")
    private String workingFilesJson;

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "rag_profile_id", nullable = false, length = 64)
    private String ragProfileId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AssistantSession() {
    }

    private AssistantSession(
            Long userId,
            String title,
            boolean globalScope,
            String workingFilesJson,
            String messagesJson,
            String ragProfileId
    ) {
        this.userId = userId;
        this.title = title;
        this.globalScope = globalScope;
        this.workingFilesJson = workingFilesJson;
        this.messagesJson = messagesJson;
        this.ragProfileId = ragProfileId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static AssistantSession create(
            Long userId,
            String title,
            boolean globalScope,
            String workingFilesJson,
            String messagesJson,
            String ragProfileId
    ) {
        return new AssistantSession(userId, title, globalScope, workingFilesJson, messagesJson, ragProfileId);
    }

    public void sync(
            String title,
            String summary,
            boolean globalScope,
            String workingFilesJson,
            String messagesJson,
            String ragProfileId
    ) {
        this.title = title;
        this.summary = summary;
        this.globalScope = globalScope;
        this.workingFilesJson = workingFilesJson;
        this.messagesJson = messagesJson;
        this.ragProfileId = ragProfileId;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isGlobalScope() {
        return globalScope;
    }

    public String getWorkingFilesJson() {
        return workingFilesJson;
    }

    public String getMessagesJson() {
        return messagesJson;
    }

    public String getRagProfileId() {
        return ragProfileId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
