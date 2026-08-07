package com.vernflow.modules.openclaw.service;

import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.exception.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OpenClawShadowRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClawShadowRunner.class);

    private final OpenClawSessionContextService contextService;
    private final OpenClawGatewayClient gatewayClient;
    private final OpenClawShadowRunStore runStore;
    private final OpenClawProperties properties;

    public OpenClawShadowRunner(
            OpenClawSessionContextService contextService,
            OpenClawGatewayClient gatewayClient,
            OpenClawShadowRunStore runStore,
            OpenClawProperties properties
    ) {
        this.contextService = contextService;
        this.gatewayClient = gatewayClient;
        this.runStore = runStore;
        this.properties = properties;
    }

    public void run(OpenClawShadowTask task) {
        long startedNanos = System.nanoTime();
        Long runId;
        try {
            runId = runStore.start(task);
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenClaw shadow run could not create its audit record; sessionId={}", task.assistantSessionId());
            return;
        }

        try {
            OpenClawSessionContextService.BoundSession boundSession = contextService.bind(
                    task.userId(),
                    task.assistantSessionId(),
                    task.globalScope(),
                    task.workingFiles()
            );
            runStore.attachSession(runId, boundSession.sessionKey());

            OpenClawGatewayClient.ShadowResponse response = gatewayClient.createShadowResponse(
                    boundSession.sessionKey(),
                    task.prompt()
            );
            runStore.succeed(
                    runId,
                    response,
                    elapsedMillis(startedNanos),
                    properties.effectiveShadowMaxStoredChars()
            );
            LOGGER.info("OpenClaw shadow run succeeded; runId={}, sessionId={}", runId, task.assistantSessionId());
        } catch (RuntimeException exception) {
            String errorCode = classify(exception);
            try {
                runStore.fail(runId, errorCode, elapsedMillis(startedNanos));
            } catch (RuntimeException ignored) {
                LOGGER.warn("OpenClaw shadow run could not persist its failure; runId={}", runId);
            }
            LOGGER.warn(
                    "OpenClaw shadow run failed; runId={}, sessionId={}, errorCode={}",
                    runId,
                    task.assistantSessionId(),
                    errorCode
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String classify(RuntimeException exception) {
        if (exception instanceof OpenClawGatewayClient.GatewayCallException gatewayException) {
            return gatewayException.errorCode();
        }
        if (exception instanceof BusinessException) {
            return "context_binding_failed";
        }
        return "unexpected_error";
    }
}
