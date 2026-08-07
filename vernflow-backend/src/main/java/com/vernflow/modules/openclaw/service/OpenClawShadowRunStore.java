package com.vernflow.modules.openclaw.service;

import com.vernflow.modules.openclaw.domain.OpenClawShadowRun;
import com.vernflow.modules.openclaw.repository.OpenClawShadowRunRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenClawShadowRunStore {

    private final OpenClawShadowRunRepository repository;

    public OpenClawShadowRunStore(OpenClawShadowRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Long start(OpenClawShadowTask task) {
        OpenClawShadowRun run = OpenClawShadowRun.start(
                task.userId(),
                task.assistantSessionId(),
                task.requestMessageId(),
                task.legacyMessageId()
        );
        return repository.save(run).getId();
    }

    @Transactional
    public void attachSession(Long runId, String sessionKey) {
        repository.findById(runId).ifPresent(run -> run.attachSession(sessionKey));
    }

    @Transactional
    public void succeed(
            Long runId,
            OpenClawGatewayClient.ShadowResponse response,
            long latencyMs,
            int maxStoredChars
    ) {
        repository.findById(runId).ifPresent(run -> run.succeed(
                truncate(response.responseId(), 128),
                truncate(response.text(), maxStoredChars),
                response.inputTokens(),
                response.outputTokens(),
                latencyMs
        ));
    }

    @Transactional
    public void fail(Long runId, String errorCode, long latencyMs) {
        repository.findById(runId).ifPresent(run -> run.fail(truncate(errorCode, 64), latencyMs));
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}
