package com.vernflow.modules.openclaw.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.assistant.repository.AssistantSessionRepository;
import com.vernflow.modules.openclaw.domain.OpenClawSessionContext;
import com.vernflow.modules.openclaw.repository.OpenClawSessionContextRepository;
import com.vernflow.modules.user.service.UserAuthService;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OpenClawSessionContextService {

    private static final TypeReference<List<WorkspaceItemRef>> WORKSPACE_ITEMS_TYPE = new TypeReference<>() {
    };
    private static final Duration DEFAULT_CONTEXT_TTL = Duration.ofMinutes(30);

    private final OpenClawSessionContextRepository contextRepository;
    private final AssistantSessionRepository assistantSessionRepository;
    private final UserAuthService userAuthService;
    private final OpenClawProperties openClawProperties;
    private final ObjectMapper objectMapper;

    public OpenClawSessionContextService(
            OpenClawSessionContextRepository contextRepository,
            AssistantSessionRepository assistantSessionRepository,
            UserAuthService userAuthService,
            OpenClawProperties openClawProperties,
            ObjectMapper objectMapper
    ) {
        this.contextRepository = contextRepository;
        this.assistantSessionRepository = assistantSessionRepository;
        this.userAuthService = userAuthService;
        this.openClawProperties = openClawProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Bind a trusted VernTrip user scope to the exact key sent to OpenResponses.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BoundSession bind(
            Long userId,
            Long assistantSessionId,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles
    ) {
        userAuthService.requireUser(userId);
        if (assistantSessionId != null) {
            assistantSessionRepository.findByIdAndUserId(assistantSessionId, userId)
                    .orElseThrow(() -> new BusinessException("Assistant session was not found."));
        }

        List<WorkspaceItemRef> scopeFiles = workingFiles == null ? List.of() : List.copyOf(workingFiles);
        String workingFilesJson = writeWorkingFiles(scopeFiles);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(contextTtl());

        if (assistantSessionId != null) {
            OpenClawSessionContext existing = contextRepository
                    .findFirstByUserIdAndAssistantSessionIdOrderByUpdatedAtDesc(userId, assistantSessionId)
                    .filter(context -> context.getExpiresAt().isAfter(now))
                    .orElse(null);
            if (existing != null) {
                existing.refresh(globalScope, workingFilesJson, expiresAt);
                contextRepository.save(existing);
                return new BoundSession(existing.getSessionKey(), expiresAt);
            }
        }

        OpenClawSessionContext context = OpenClawSessionContext.create(
                "verntrip:" + UUID.randomUUID(),
                userId,
                assistantSessionId,
                globalScope,
                workingFilesJson,
                expiresAt
        );
        contextRepository.save(context);
        return new BoundSession(context.getSessionKey(), expiresAt);
    }

    @Transactional
    public ResolvedSession resolveActive(String sessionKey) {
        LocalDateTime now = LocalDateTime.now();
        OpenClawSessionContext context = contextRepository.findBySessionKey(sessionKey)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException("OpenClaw session context is missing or expired."));

        LocalDateTime refreshedExpiry = now.plus(contextTtl());
        context.refresh(context.isGlobalScope(), context.getWorkingFilesJson(), refreshedExpiry);
        contextRepository.save(context);
        return new ResolvedSession(
                context.getUserId(),
                context.getAssistantSessionId(),
                context.isGlobalScope(),
                readWorkingFiles(context.getWorkingFilesJson()),
                refreshedExpiry
        );
    }

    private Duration contextTtl() {
        Duration configured = openClawProperties.sessionContextTtl();
        return configured == null || configured.isNegative() || configured.isZero()
                ? DEFAULT_CONTEXT_TTL
                : configured;
    }

    private String writeWorkingFiles(List<WorkspaceItemRef> workingFiles) {
        try {
            return objectMapper.writeValueAsString(workingFiles);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to persist OpenClaw workspace scope.");
        }
    }

    private List<WorkspaceItemRef> readWorkingFiles(String workingFilesJson) {
        try {
            return objectMapper.readValue(workingFilesJson, WORKSPACE_ITEMS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("OpenClaw workspace scope is invalid.");
        }
    }

    public record BoundSession(String sessionKey, LocalDateTime expiresAt) {
    }

    public record ResolvedSession(
            Long userId,
            Long assistantSessionId,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            LocalDateTime expiresAt
    ) {
    }
}
