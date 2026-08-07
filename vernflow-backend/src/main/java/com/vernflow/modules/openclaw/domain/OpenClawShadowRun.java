package com.vernflow.modules.openclaw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Evaluation record for an OpenClaw response that was never shown to the user.
 */
@Entity
@Table(name = "openclaw_shadow_run")
public class OpenClawShadowRun {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "assistant_session_id", nullable = false)
    private Long assistantSessionId;

    @Column(name = "request_message_id", length = 96)
    private String requestMessageId;

    @Column(name = "legacy_message_id", length = 96)
    private String legacyMessageId;

    @Column(name = "session_key", length = 96)
    private String sessionKey;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "response_id", length = 128)
    private String responseId;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected OpenClawShadowRun() {
    }

    private OpenClawShadowRun(
            Long userId,
            Long assistantSessionId,
            String requestMessageId,
            String legacyMessageId
    ) {
        this.userId = userId;
        this.assistantSessionId = assistantSessionId;
        this.requestMessageId = requestMessageId;
        this.legacyMessageId = legacyMessageId;
        this.status = STATUS_RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public static OpenClawShadowRun start(
            Long userId,
            Long assistantSessionId,
            String requestMessageId,
            String legacyMessageId
    ) {
        return new OpenClawShadowRun(userId, assistantSessionId, requestMessageId, legacyMessageId);
    }

    public void attachSession(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public void succeed(
            String responseId,
            String responseText,
            Long inputTokens,
            Long outputTokens,
            long latencyMs
    ) {
        this.status = STATUS_SUCCEEDED;
        this.responseId = responseId;
        this.responseText = responseText;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.errorCode = null;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, long latencyMs) {
        this.status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.latencyMs = latencyMs;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAssistantSessionId() {
        return assistantSessionId;
    }

    public String getRequestMessageId() {
        return requestMessageId;
    }

    public String getLegacyMessageId() {
        return legacyMessageId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getStatus() {
        return status;
    }

    public String getResponseId() {
        return responseId;
    }

    public String getResponseText() {
        return responseText;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
