package com.vernflow.modules.openclaw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Server-owned mapping from an opaque OpenClaw session key to VernTrip scope.
 */
@Entity
@Table(name = "openclaw_session_context")
public class OpenClawSessionContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, unique = true, length = 96)
    private String sessionKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "assistant_session_id")
    private Long assistantSessionId;

    @Column(name = "global_scope", nullable = false)
    private boolean globalScope;

    @Column(name = "working_files_json", nullable = false, columnDefinition = "TEXT")
    private String workingFilesJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OpenClawSessionContext() {
    }

    private OpenClawSessionContext(
            String sessionKey,
            Long userId,
            Long assistantSessionId,
            boolean globalScope,
            String workingFilesJson,
            LocalDateTime expiresAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.assistantSessionId = assistantSessionId;
        this.globalScope = globalScope;
        this.workingFilesJson = workingFilesJson;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OpenClawSessionContext create(
            String sessionKey,
            Long userId,
            Long assistantSessionId,
            boolean globalScope,
            String workingFilesJson,
            LocalDateTime expiresAt
    ) {
        return new OpenClawSessionContext(
                sessionKey,
                userId,
                assistantSessionId,
                globalScope,
                workingFilesJson,
                expiresAt
        );
    }

    public void refresh(boolean globalScope, String workingFilesJson, LocalDateTime expiresAt) {
        this.globalScope = globalScope;
        this.workingFilesJson = workingFilesJson;
        this.expiresAt = expiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAssistantSessionId() {
        return assistantSessionId;
    }

    public boolean isGlobalScope() {
        return globalScope;
    }

    public String getWorkingFilesJson() {
        return workingFilesJson;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
