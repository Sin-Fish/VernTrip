package com.vernflow.modules.openclaw.service;

import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.assistant.dto.AssistantAskRequest;
import com.vernflow.modules.assistant.dto.AssistantMessagePayload;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Best-effort dispatcher. No exception from this component may affect the user response.
 */
@Component
public class OpenClawShadowDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClawShadowDispatcher.class);

    private final OpenClawProperties properties;
    private final Executor executor;
    private final OpenClawShadowRunner runner;

    public OpenClawShadowDispatcher(
            OpenClawProperties properties,
            @Qualifier("openClawShadowTaskExecutor") Executor executor,
            OpenClawShadowRunner runner
    ) {
        this.properties = properties;
        this.executor = executor;
        this.runner = runner;
    }

    public void submit(
            Long userId,
            AssistantAskRequest request,
            AssistantSessionDetailResponse legacyResponse
    ) {
        try {
            if (!properties.shadowEnabled() || !sampled()) {
                return;
            }

            OpenClawShadowTask task = new OpenClawShadowTask(
                    userId,
                    legacyResponse.id(),
                    latestMessageId(legacyResponse.messages(), "user"),
                    latestMessageId(legacyResponse.messages(), "assistant"),
                    request.prompt().trim(),
                    legacyResponse.globalScope(),
                    immutableFiles(legacyResponse.workingFiles())
            );
            executor.execute(() -> runner.run(task));
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenClaw shadow request was not queued; sessionId={}", legacyResponse.id());
        }
    }

    private boolean sampled() {
        double rate = properties.effectiveShadowSampleRate();
        return rate >= 1.0 || (rate > 0.0 && ThreadLocalRandom.current().nextDouble() < rate);
    }

    private List<WorkspaceItemRef> immutableFiles(List<WorkspaceItemRef> workingFiles) {
        return workingFiles == null ? List.of() : List.copyOf(workingFiles);
    }

    private String latestMessageId(List<AssistantMessagePayload> messages, String role) {
        if (messages == null) {
            return null;
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            AssistantMessagePayload message = messages.get(index);
            if (message != null && role.equalsIgnoreCase(message.role())) {
                return message.id();
            }
        }
        return null;
    }
}
