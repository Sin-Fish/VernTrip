package com.vernflow.modules.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.assistant.domain.AssistantSession;
import com.vernflow.modules.assistant.dto.AssistantAskRequest;
import com.vernflow.modules.assistant.dto.AssistantMessagePayload;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.assistant.dto.AssistantSessionSummaryResponse;
import com.vernflow.modules.assistant.repository.AssistantSessionRepository;
import com.vernflow.modules.rag.service.RagProfileFacade;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Real workspace assistant sessions backed by local workspace files.
 */
@Service
public class WorkspaceAssistantService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final String DEFAULT_SUMMARY = "等待提问";

    private static final TypeReference<List<AssistantMessagePayload>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<WorkspaceItemRef>> WORKING_FILE_LIST_TYPE = new TypeReference<>() {
    };

    private final AssistantSessionRepository assistantSessionRepository;
    private final WorkspaceDocumentScopeService workspaceDocumentScopeService;
    private final RagProfileFacade ragProfileFacade;
    private final ObjectMapper objectMapper;

    public WorkspaceAssistantService(
            AssistantSessionRepository assistantSessionRepository,
            WorkspaceDocumentScopeService workspaceDocumentScopeService,
            RagProfileFacade ragProfileFacade,
            ObjectMapper objectMapper
    ) {
        this.assistantSessionRepository = assistantSessionRepository;
        this.workspaceDocumentScopeService = workspaceDocumentScopeService;
        this.ragProfileFacade = ragProfileFacade;
        this.objectMapper = objectMapper;
    }

    /**
     * List all saved assistant sessions.
     *
     * @return summary cards ordered by latest update
     */
    @Transactional(readOnly = true)
    public List<AssistantSessionSummaryResponse> listSessions(Long userId) {
        return assistantSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * Load a full assistant session.
     *
     * @param sessionId session id
     * @return full session detail
     */
    @Transactional(readOnly = true)
    public AssistantSessionDetailResponse getSession(Long userId, Long sessionId) {
        AssistantSession session = assistantSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("Assistant session was not found: " + sessionId));
        return toDetail(session);
    }

    @Transactional(readOnly = true)
    public AssistantSessionDetailResponse getSession(Long sessionId) {
        AssistantSession session = assistantSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Assistant session was not found: " + sessionId));
        return toDetail(session);
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        AssistantSession session = assistantSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("Assistant session was not found: " + sessionId));
        assistantSessionRepository.delete(session);
    }

    /**
     * Submit a new prompt into the current assistant session.
     *
     * @param request current scope snapshot and prompt
     * @return updated assistant session
     */
    @Transactional
    public AssistantSessionDetailResponse ask(Long userId, AssistantAskRequest request) {
        if (!StringUtils.hasText(request.prompt())) {
            throw new BusinessException("Assistant prompt cannot be empty.");
        }

        List<WorkspaceItemRef> workingFiles = normalizeWorkingFiles(request.workingFiles());
        WorkspaceDocumentScopeService.ScopeExtractionResult scope = workspaceDocumentScopeService.collectScope(
                userId,
                request.globalScope(),
                workingFiles
        );

        AssistantSession session = request.sessionId() == null
                ? AssistantSession.create(
                        userId,
                        buildTitle(request.prompt()),
                        request.globalScope(),
                        writeWorkingFiles(workingFiles),
                        "[]",
                        ragProfileFacade.defaultProfileId()
                )
                : loadSession(userId, request.sessionId());

        List<AssistantMessagePayload> priorMessages = new ArrayList<>(readMessages(session));
        List<AssistantMessagePayload> messages = new ArrayList<>(priorMessages);
        messages.add(new AssistantMessagePayload(
                generateMessageId("user"),
                "user",
                request.prompt().trim(),
                nowString(),
                List.of()
        ));

        AssistantAnswer answer = buildAnswer(
                userId,
                request.prompt().trim(),
                request.globalScope(),
                scope,
                priorMessages
        );
        messages.add(new AssistantMessagePayload(
                generateMessageId("assistant"),
                "assistant",
                answer.text(),
                nowString(),
                answer.cites()
        ));

        session.sync(
                deriveTitle(session.getTitle(), request.prompt()),
                buildSummary(request.prompt()),
                request.globalScope(),
                writeWorkingFiles(workingFiles),
                writeMessages(messages),
                ragProfileFacade.defaultProfileId()
        );
        assistantSessionRepository.save(session);
        return toDetail(session, workingFiles, messages);
    }

    private AssistantAnswer buildAnswer(
            Long userId,
            String prompt,
            boolean globalScope,
            WorkspaceDocumentScopeService.ScopeExtractionResult scope,
            List<AssistantMessagePayload> priorMessages
    ) {
        if (scope.totalScopeFileCount() == 0) {
            return new AssistantAnswer(
                    "当前范围里还没有可读取的文件。先在资料库里加入工作文件，或开启全局工作区后再提问。",
                    List.of()
            );
        }

        List<AiPaperClient.ConversationMessage> conversationContext = buildConversationContext(priorMessages);
        AssistantAnswer answer = buildAnswerViaProfile(
                userId,
                prompt,
                globalScope,
                scope.readableDocuments(),
                conversationContext
        );
        if (answer != null) {
            return answer;
        }

        return buildFallbackAnswer(prompt, globalScope, scope);
    }

    private AssistantAnswer buildAnswerViaProfile(
            Long userId,
            String prompt,
            boolean globalScope,
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> readableDocuments,
            List<AiPaperClient.ConversationMessage> conversationContext
    ) {
        try {
            AiPaperClient.RagQueryResponse response = ragProfileFacade.queryFromScope(
                    userId,
                    prompt,
                    globalScope ? "global workspace" : "working files",
                    readableDocuments,
                    globalScope,
                    conversationContext
            );
            List<String> cites = response.evidences().stream()
                    .map(AiPaperClient.RagEvidence::paperTitle)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(6)
                    .toList();

            return new AssistantAnswer(response.answer(), cites);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private AssistantAnswer buildFallbackAnswer(
            String prompt,
            boolean globalScope,
            WorkspaceDocumentScopeService.ScopeExtractionResult scope
    ) {
        if (scope.readableDocuments().isEmpty()) {
            List<String> cites = scope.unreadableFiles().stream()
                    .distinct()
                    .limit(6)
                    .toList();
            String scopeLabel = globalScope ? "全局工作区" : "工作文件";
            String fileList = cites.isEmpty() ? "当前范围里的文件" : String.join("、", cites);
            return new AssistantAnswer(
                    "我已经读取了当前会话的范围配置，但这批文件暂时没有可直接抽取的正文内容。"
                            + "当前范围是“" + scopeLabel + "”，涉及的文件包括：" + fileList + "。"
                            + "你可以继续上传 markdown、txt、代码、docx、pptx、xlsx 等可抽取文本的文件，"
                            + "或者把问题缩小到已有文本资料上。",
                    cites
            );
        }

        List<String> cites = scope.readableDocuments().stream()
                .map(WorkspaceDocumentScopeService.ReadableWorkspaceDocument::citeLabel)
                .distinct()
                .limit(6)
                .toList();
        String highlights = scope.readableDocuments().stream()
                .limit(3)
                .map(document -> {
                    String firstChunk = document.chunks().getFirst();
                    String excerpt = firstChunk.length() > 120 ? firstChunk.substring(0, 120) + "..." : firstChunk;
                    return "《" + document.citeLabel() + "》提到：" + excerpt;
                })
                .collect(Collectors.joining("\n"));

        return new AssistantAnswer(
                "AI 检索服务暂时不可用，我先根据当前范围里的可读文件给你一版本地摘要。\n"
                        + "你的问题是：“" + prompt + "”。\n"
                        + highlights
                        + "\n如果你需要更精确的答案，可以稍后再试一次，或缩小到更少的工作文件。",
                cites
        );
    }

    private AssistantSessionSummaryResponse toSummary(AssistantSession session) {
        List<AssistantMessagePayload> messages = readMessages(session);
        return new AssistantSessionSummaryResponse(
                session.getId(),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : DEFAULT_TITLE,
                resolveSummary(session, messages),
                messages.size(),
                session.getUpdatedAt()
        );
    }

    private AssistantSessionDetailResponse toDetail(AssistantSession session) {
        return toDetail(session, readWorkingFiles(session), readMessages(session));
    }

    private AssistantSessionDetailResponse toDetail(
            AssistantSession session,
            List<WorkspaceItemRef> workingFiles,
            List<AssistantMessagePayload> messages
    ) {
        return new AssistantSessionDetailResponse(
                session.getId(),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : DEFAULT_TITLE,
                resolveSummary(session, messages),
                session.isGlobalScope(),
                workingFiles,
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messages
        );
    }

    private List<WorkspaceItemRef> normalizeWorkingFiles(List<WorkspaceItemRef> workingFiles) {
        if (workingFiles == null || workingFiles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        List<WorkspaceItemRef> normalized = new ArrayList<>();
        for (WorkspaceItemRef item : workingFiles) {
            if (item == null || !StringUtils.hasText(item.path()) || !StringUtils.hasText(item.label())) {
                continue;
            }
            if (!seenPaths.add(item.path())) {
                continue;
            }
            normalized.add(new WorkspaceItemRef(
                    item.path(),
                    item.label(),
                    "file".equalsIgnoreCase(item.kind()) ? "file" : "folder"
            ));
        }
        return normalized;
    }

    private List<AiPaperClient.ConversationMessage> buildConversationContext(List<AssistantMessagePayload> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<AiPaperClient.ConversationMessage> normalizedMessages = messages.stream()
                .filter(message -> message != null
                        && StringUtils.hasText(message.role())
                        && StringUtils.hasText(message.text()))
                .map(message -> new AiPaperClient.ConversationMessage(
                        normalizeConversationRole(message.role()),
                        buildConversationMessageText(message.text())
                ))
                .filter(message -> StringUtils.hasText(message.text()))
                .toList();

        if (normalizedMessages.size() <= 6) {
            return normalizedMessages;
        }
        return normalizedMessages.subList(normalizedMessages.size() - 6, normalizedMessages.size());
    }

    private String normalizeConversationRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private String buildConversationMessageText(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private List<AssistantMessagePayload> readMessages(AssistantSession session) {
        if (!StringUtils.hasText(session.getMessagesJson())) {
            return List.of();
        }

        try {
            List<AssistantMessagePayload> messages = objectMapper.readValue(session.getMessagesJson(), MESSAGE_LIST_TYPE);
            return messages == null ? List.of() : messages;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to read assistant messages.");
        }
    }

    private List<WorkspaceItemRef> readWorkingFiles(AssistantSession session) {
        if (!StringUtils.hasText(session.getWorkingFilesJson())) {
            return List.of();
        }

        try {
            List<WorkspaceItemRef> workingFiles = objectMapper.readValue(session.getWorkingFilesJson(), WORKING_FILE_LIST_TYPE);
            return workingFiles == null ? List.of() : workingFiles;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to read assistant working files.");
        }
    }

    private String writeMessages(List<AssistantMessagePayload> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to store assistant messages.");
        }
    }

    private String writeWorkingFiles(List<WorkspaceItemRef> workingFiles) {
        try {
            return objectMapper.writeValueAsString(workingFiles);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to store assistant working files.");
        }
    }

    private AssistantSession loadSession(Long userId, Long sessionId) {
        return assistantSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("Assistant session was not found: " + sessionId));
    }

    private String deriveTitle(String existingTitle, String prompt) {
        if (StringUtils.hasText(existingTitle)
                && !DEFAULT_TITLE.equals(existingTitle)
                && !"New conversation".equalsIgnoreCase(existingTitle)) {
            return existingTitle;
        }
        return buildTitle(prompt);
    }

    private String buildTitle(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return normalized.length() > 28 ? normalized.substring(0, 28) + "..." : normalized;
    }

    private String buildSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return DEFAULT_SUMMARY;
        }

        String concise = text.replaceAll("\\s+", " ").trim();
        return concise.length() > 96 ? concise.substring(0, 96) + "..." : concise;
    }

    private String resolveSummary(AssistantSession session, List<AssistantMessagePayload> messages) {
        String latestUserPrompt = extractLatestUserPrompt(messages);
        if (StringUtils.hasText(latestUserPrompt)) {
            return buildSummary(latestUserPrompt);
        }

        if (StringUtils.hasText(session.getSummary())) {
            return session.getSummary();
        }

        return DEFAULT_SUMMARY;
    }

    private String extractLatestUserPrompt(List<AssistantMessagePayload> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AssistantMessagePayload message = messages.get(index);
            if (message != null && "user".equalsIgnoreCase(message.role()) && StringUtils.hasText(message.text())) {
                return message.text();
            }
        }

        return null;
    }

    private String nowString() {
        return LocalDateTime.now().toString();
    }

    private String generateMessageId(String role) {
        return role.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
    }

    private record AssistantAnswer(String text, List<String> cites) {
    }
}
