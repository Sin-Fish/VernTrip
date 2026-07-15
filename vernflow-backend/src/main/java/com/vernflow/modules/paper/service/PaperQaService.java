package com.vernflow.modules.paper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.domain.Paper;
import com.vernflow.modules.paper.domain.PaperChunk;
import com.vernflow.modules.paper.domain.PaperRelation;
import com.vernflow.modules.paper.domain.QaEvidence;
import com.vernflow.modules.paper.domain.QaScope;
import com.vernflow.modules.paper.domain.QaSession;
import com.vernflow.modules.paper.dto.QaRequest;
import com.vernflow.modules.paper.dto.QaResponse;
import com.vernflow.modules.paper.dto.QaSessionDetailResponse;
import com.vernflow.modules.paper.dto.QaSessionMessagePayload;
import com.vernflow.modules.paper.dto.QaSessionSaveRequest;
import com.vernflow.modules.paper.dto.QaSessionSummaryResponse;
import com.vernflow.modules.paper.repository.PaperChunkRepository;
import com.vernflow.modules.paper.repository.PaperRelationRepository;
import com.vernflow.modules.paper.repository.PaperRepository;
import com.vernflow.modules.paper.repository.QaSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaperQaService {

    private static final TypeReference<List<QaSessionMessagePayload>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final PaperRepository paperRepository;
    private final PaperChunkRepository paperChunkRepository;
    private final PaperRelationRepository paperRelationRepository;
    private final QaSessionRepository qaSessionRepository;
    private final AiPaperClient aiPaperClient;
    private final ObjectMapper objectMapper;

    public PaperQaService(
            PaperRepository paperRepository,
            PaperChunkRepository paperChunkRepository,
            PaperRelationRepository paperRelationRepository,
            QaSessionRepository qaSessionRepository,
            AiPaperClient aiPaperClient,
            ObjectMapper objectMapper
    ) {
        this.paperRepository = paperRepository;
        this.paperChunkRepository = paperChunkRepository;
        this.paperRelationRepository = paperRelationRepository;
        this.qaSessionRepository = qaSessionRepository;
        this.aiPaperClient = aiPaperClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QaResponse ask(QaRequest request) {
        QaSession existingSession = request.sessionId() == null ? null : loadSessionForUpdate(request.sessionId());
        QaScope scope = resolveScope(request, existingSession);
        Paper centerPaper = resolvePaperForRequest(request, existingSession);
        List<Long> scopePaperIds = resolveScopePaperIds(centerPaper, scope);
        if (scopePaperIds.isEmpty()) {
            throw new BusinessException("当前问答范围下没有可用于检索的论文内容");
        }

        Map<Long, Paper> paperMap = paperRepository.findAllById(scopePaperIds).stream()
                .collect(Collectors.toMap(Paper::getId, paper -> paper));
        List<PaperChunk> chunks = paperChunkRepository.findAllByPaperIdInOrderByPaperIdAscSortIndexAsc(scopePaperIds)
                .stream()
                .limit(240)
                .toList();
        List<AiPaperClient.RagChunk> ragChunks = chunks.stream()
                .map(chunk -> new AiPaperClient.RagChunk(
                        chunk.getPaper().getId(),
                        paperMap.get(chunk.getPaper().getId()).getTitle(),
                        chunk.getSectionTitle(),
                        chunk.getPageNo(),
                        chunk.getChunkText()
                ))
                .toList();

        if (ragChunks.isEmpty()) {
            throw new BusinessException("当前问题没有命中可用证据，暂时无法生成稳定回答");
        }

        AiPaperClient.RagQueryResponse ragResponse = aiPaperClient.askQuestion(request.question(), scope, ragChunks);

        QaSession session = existingSession == null
                ? QaSession.create(null, centerPaper, request.question(), scope)
                : existingSession;

        String assistantMessageId = generateMessageId("assistant");
        List<QaSessionMessagePayload.Evidence> answerEvidences = ragResponse.evidences().stream()
                .map(evidence -> new QaSessionMessagePayload.Evidence(
                        evidence.paperId(),
                        evidence.paperTitle(),
                        evidence.sectionTitle(),
                        evidence.pageNo(),
                        evidence.excerpt(),
                        evidence.score()
                ))
                .toList();

        List<QaSessionMessagePayload> messages = new ArrayList<>(readMessages(session));
        messages.add(new QaSessionMessagePayload(
                generateMessageId("user"),
                "user",
                request.question(),
                nowString(),
                scope,
                List.of()
        ));
        messages.add(new QaSessionMessagePayload(
                assistantMessageId,
                "assistant",
                ragResponse.answer(),
                nowString(),
                scope,
                answerEvidences
        ));

        String title = StringUtils.hasText(session.getTitle()) ? session.getTitle() : buildTitle(request.question());
        String summary = buildSessionSummary(title, messages);
        session.syncWorkbenchState(
                title,
                summary,
                scope,
                centerPaper,
                false,
                session.isShowEvidence(),
                session.isShowSources(),
                session.isShowPageNumbers(),
                writeMessages(messages),
                request.question(),
                ragResponse.answer()
        );

        ragResponse.evidences().forEach(evidence -> session.addEvidence(QaEvidence.create(
                matchChunk(chunks, evidence),
                paperMap.get(evidence.paperId()),
                assistantMessageId,
                buildRelationPath(centerPaper, scope, evidence.paperId()),
                evidence.score(),
                evidence.excerpt()
        )));
        qaSessionRepository.save(session);

        return new QaResponse(
                session.getId(),
                ragResponse.answer(),
                scope,
                answerEvidences.stream()
                        .map(evidence -> new QaResponse.Evidence(
                                evidence.paperId(),
                                evidence.paperTitle(),
                                evidence.sectionTitle(),
                                evidence.pageNo(),
                                evidence.excerpt(),
                                evidence.score() == null ? 0 : evidence.score()
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<QaSessionSummaryResponse> listSessions(Long paperId) {
        List<QaSession> sessions = paperId == null
                ? qaSessionRepository.findAllByOrderByUpdatedAtDesc()
                : qaSessionRepository.findAllByPaperIdOrderByUpdatedAtDesc(paperId);
        return sessions.stream()
                .map(session -> toSessionSummary(session, readMessages(session)))
                .toList();
    }

    @Transactional(readOnly = true)
    public QaSessionDetailResponse getSession(Long sessionId) {
        QaSession session = qaSessionRepository.findWithEvidencesById(sessionId)
                .orElseThrow(() -> new BusinessException("未找到对应问答会话: " + sessionId));
        List<QaSessionMessagePayload> messages = readMessages(session);
        return toSessionDetail(session, messages);
    }

    @Transactional
    public QaSessionDetailResponse saveSession(QaSessionSaveRequest request) {
        QaSession session = request.id() == null
                ? QaSession.create(null, resolvePaper(request.paperId()), seedQuestion(request), resolveSnapshotScope(request))
                : loadSessionForUpdate(request.id());

        Paper paper = resolvePaper(request.paperId());
        List<QaSessionMessagePayload> messages = request.messages() == null ? List.of() : request.messages();
        String latestQuestion = latestQuestion(messages);
        String latestAnswer = latestAnswer(messages);
        String title = StringUtils.hasText(request.title())
                ? request.title()
                : buildTitle(StringUtils.hasText(latestQuestion) ? latestQuestion : seedQuestion(request));
        String summary = StringUtils.hasText(request.summary())
                ? request.summary()
                : buildSessionSummary(title, messages);

        session.syncWorkbenchState(
                title,
                summary,
                resolveSnapshotScope(request),
                paper,
                request.archived(),
                request.showEvidence(),
                request.showSources(),
                request.showPageNumbers(),
                writeMessages(messages),
                latestQuestion,
                latestAnswer
        );
        qaSessionRepository.save(session);
        return toSessionDetail(session, messages);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        if (!qaSessionRepository.existsById(sessionId)) {
            throw new BusinessException("未找到对应问答会话: " + sessionId);
        }
        qaSessionRepository.deleteById(sessionId);
    }

    private QaScope resolveScope(QaRequest request, QaSession existingSession) {
        if (request.scope() != null) {
            return request.scope();
        }
        if (existingSession != null && existingSession.getScope() != null) {
            return existingSession.getScope();
        }
        return QaScope.CURRENT_PAPER;
    }

    private QaScope resolveSnapshotScope(QaSessionSaveRequest request) {
        return request.scope() == null ? QaScope.CURRENT_PAPER : request.scope();
    }

    private Paper resolvePaperForRequest(QaRequest request, QaSession existingSession) {
        if (request.paperId() != null) {
            return loadPaper(request.paperId());
        }
        if (existingSession != null) {
            return existingSession.getPaper();
        }
        return null;
    }

    private Paper resolvePaper(Long paperId) {
        return paperId == null ? null : loadPaper(paperId);
    }

    private String seedQuestion(QaSessionSaveRequest request) {
        String latestQuestion = latestQuestion(request.messages() == null ? List.of() : request.messages());
        if (StringUtils.hasText(latestQuestion)) {
            return latestQuestion;
        }
        if (StringUtils.hasText(request.title())) {
            return request.title();
        }
        return "新建会话";
    }

    private String latestQuestion(List<QaSessionMessagePayload> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second)
                .map(QaSessionMessagePayload::content)
                .orElse(null);
    }

    private String latestAnswer(List<QaSessionMessagePayload> messages) {
        return messages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second)
                .map(QaSessionMessagePayload::content)
                .orElse(null);
    }

    private List<QaSessionMessagePayload> readMessages(QaSession session) {
        if (StringUtils.hasText(session.getMessagesJson())) {
            try {
                List<QaSessionMessagePayload> parsed = objectMapper.readValue(session.getMessagesJson(), MESSAGE_LIST_TYPE);
                return parsed == null ? List.of() : parsed;
            } catch (JsonProcessingException exception) {
                throw new BusinessException("问答会话消息解析失败");
            }
        }

        List<QaSessionMessagePayload.Evidence> latestEvidences = session.getEvidences().stream()
                .map(this::toMessageEvidence)
                .toList();

        List<QaSessionMessagePayload> fallback = new ArrayList<>();
        if (StringUtils.hasText(session.getQuestion())) {
            fallback.add(new QaSessionMessagePayload(
                    generateMessageId("user"),
                    "user",
                    session.getQuestion(),
                    session.getCreatedAt() == null ? nowString() : session.getCreatedAt().toString(),
                    session.getScope(),
                    List.of()
            ));
        }
        if (StringUtils.hasText(session.getAnswer())) {
            fallback.add(new QaSessionMessagePayload(
                    generateMessageId("assistant"),
                    "assistant",
                    session.getAnswer(),
                    session.getUpdatedAt() == null ? nowString() : session.getUpdatedAt().toString(),
                    session.getScope(),
                    latestEvidences
            ));
        }
        return fallback;
    }

    private String writeMessages(List<QaSessionMessagePayload> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("问答会话消息序列化失败");
        }
    }

    private QaSessionSummaryResponse toSessionSummary(QaSession session, List<QaSessionMessagePayload> messages) {
        Paper paper = session.getPaper();
        return new QaSessionSummaryResponse(
                session.getId(),
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : buildTitle(session.getQuestion()),
                session.getQuestion(),
                session.getScope(),
                session.getSummary(),
                session.isArchived(),
                messages.size(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private QaSessionDetailResponse toSessionDetail(QaSession session, List<QaSessionMessagePayload> messages) {
        Paper paper = session.getPaper();
        List<QaSessionDetailResponse.Evidence> latestEvidences = latestAssistantMessage(messages)
                .map(QaSessionMessagePayload::evidences)
                .orElse(List.of())
                .stream()
                .map(evidence -> new QaSessionDetailResponse.Evidence(
                        null,
                        evidence.paperId(),
                        evidence.paperTitle(),
                        null,
                        evidence.sectionTitle(),
                        evidence.pageNo(),
                        null,
                        evidence.excerpt(),
                        evidence.score()
                ))
                .toList();

        return new QaSessionDetailResponse(
                session.getId(),
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                StringUtils.hasText(session.getTitle()) ? session.getTitle() : buildTitle(session.getQuestion()),
                session.getSummary(),
                session.getQuestion(),
                session.getAnswer(),
                session.getScope(),
                session.isArchived(),
                session.isShowEvidence(),
                session.isShowSources(),
                session.isShowPageNumbers(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messages,
                latestEvidences.isEmpty()
                        ? session.getEvidences().stream().map(this::toDetailEvidence).toList()
                        : latestEvidences
        );
    }

    private java.util.Optional<QaSessionMessagePayload> latestAssistantMessage(List<QaSessionMessagePayload> messages) {
        return messages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .reduce((first, second) -> second);
    }

    private QaSessionDetailResponse.Evidence toDetailEvidence(QaEvidence evidence) {
        Paper paper = evidence.getPaper();
        PaperChunk chunk = evidence.getChunk();
        return new QaSessionDetailResponse.Evidence(
                evidence.getId(),
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                chunk == null ? null : chunk.getId(),
                chunk == null ? null : chunk.getSectionTitle(),
                chunk == null ? null : chunk.getPageNo(),
                evidence.getRelationPath(),
                evidence.getQuoteText(),
                evidence.getScore()
        );
    }

    private QaSessionMessagePayload.Evidence toMessageEvidence(QaEvidence evidence) {
        Paper paper = evidence.getPaper();
        PaperChunk chunk = evidence.getChunk();
        return new QaSessionMessagePayload.Evidence(
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                chunk == null ? null : chunk.getSectionTitle(),
                chunk == null ? null : chunk.getPageNo(),
                evidence.getQuoteText(),
                evidence.getScore()
        );
    }

    private String buildTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "新建会话";
        }
        return question.length() > 34 ? question.substring(0, 34) + "..." : question;
    }

    private String buildSessionSummary(String title, List<QaSessionMessagePayload> messages) {
        String latestAnswer = latestAnswer(messages);
        if (!StringUtils.hasText(latestAnswer)) {
            return title;
        }
        String conciseAnswer = latestAnswer.replaceAll("\\s+", " ").trim();
        return conciseAnswer.length() > 120 ? conciseAnswer.substring(0, 120) + "..." : conciseAnswer;
    }

    private String nowString() {
        return LocalDateTime.now().toString();
    }

    private String generateMessageId(String role) {
        return role + "-" + UUID.randomUUID();
    }

    private List<Long> resolveScopePaperIds(Paper centerPaper, QaScope scope) {
        return switch (scope) {
            case CURRENT_PAPER -> List.of(requirePaper(centerPaper).getId());
            case RELATED_PAPERS -> {
                Long centerPaperId = requirePaper(centerPaper).getId();
                LinkedHashSet<Long> relatedPaperIds = new LinkedHashSet<>();
                relatedPaperIds.add(centerPaperId);
                paperRelationRepository.findBySourcePaperIdOrTargetPaperId(centerPaperId, centerPaperId)
                        .forEach(relation -> {
                            relatedPaperIds.add(relation.getSourcePaper().getId());
                            relatedPaperIds.add(relation.getTargetPaper().getId());
                        });
                yield List.copyOf(relatedPaperIds);
            }
            case ENTIRE_LIBRARY -> paperRepository.findAll().stream().map(Paper::getId).toList();
        };
    }

    private Paper requirePaper(Paper paper) {
        if (paper == null) {
            throw new BusinessException("当前范围下没有找到可作为证据的论文分块");
        }
        return paper;
    }

    private Paper loadPaper(Long paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new BusinessException("未找到对应论文: " + paperId));
    }

    private QaSession loadSessionForUpdate(Long sessionId) {
        return qaSessionRepository.findWithEvidencesById(sessionId)
                .orElseThrow(() -> new BusinessException("未找到对应问答会话: " + sessionId));
    }

    private PaperChunk matchChunk(List<PaperChunk> chunks, AiPaperClient.RagEvidence evidence) {
        return chunks.stream()
                .filter(chunk -> chunk.getPaper().getId().equals(evidence.paperId()))
                .filter(chunk -> evidence.pageNo() == null || chunk.getPageNo() == null || evidence.pageNo().equals(chunk.getPageNo()))
                .filter(chunk -> !StringUtils.hasText(evidence.sectionTitle())
                        || !StringUtils.hasText(chunk.getSectionTitle())
                        || evidence.sectionTitle().equalsIgnoreCase(chunk.getSectionTitle()))
                .filter(chunk -> !StringUtils.hasText(evidence.excerpt())
                        || chunk.getChunkText().contains(evidence.excerpt())
                        || evidence.excerpt().contains(chunk.getChunkText()))
                .min(Comparator.comparingInt(chunk -> chunk.getChunkText().length()))
                .orElseGet(() -> chunks.stream()
                        .filter(chunk -> chunk.getPaper().getId().equals(evidence.paperId()))
                        .findFirst()
                        .orElse(null));
    }

    private String buildRelationPath(Paper centerPaper, QaScope scope, Long evidencePaperId) {
        if (centerPaper == null || evidencePaperId == null || scope != QaScope.RELATED_PAPERS) {
            return null;
        }
        if (centerPaper.getId().equals(evidencePaperId)) {
            return centerPaper.getId().toString();
        }
        return centerPaper.getId() + "->" + evidencePaperId;
    }
}
