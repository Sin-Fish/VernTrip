package com.vernflow.modules.paper.mapper;

import com.vernflow.common.util.TextNormalizer;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;

import java.util.List;
import java.util.Map;

public final class PaperMapper {

    private PaperMapper() {
    }

    public static PaperItemResponse toItem(Paper paper) {
        return new PaperItemResponse(
                paper.getId(),
                paper.getTitle(),
                paper.getAuthors(),
                paper.getPublicationYear(),
                paper.getVenue(),
                paper.getAiSummary(),
                paper.keywordList(),
                paper.getParseStatus(),
                paper.getCreatedAt()
        );
    }

    public static PaperReferenceResponse toReference(PaperReference reference, Map<Long, String> matchedTitles) {
        return new PaperReferenceResponse(
                reference.getId(),
                reference.getRawReference(),
                reference.getRefTitle(),
                reference.getRefAuthors(),
                reference.getRefYear(),
                reference.getMatchedPaperId(),
                matchedTitles.get(reference.getMatchedPaperId()),
                reference.getMatchScore()
        );
    }

    public static PaperDetailResponse toDetail(
            Paper paper,
            List<PaperReferenceResponse> references,
            long outboundCount,
            long inboundCount,
            ParseTask latestTask
    ) {
        return new PaperDetailResponse(
                paper.getId(),
                paper.getTitle(),
                paper.getAuthors(),
                paper.getPublicationYear(),
                paper.getVenue(),
                paper.getAbstractText(),
                paper.getAiSummary(),
                paper.getSummaryProblem(),
                paper.getSummaryMethod(),
                paper.getSummaryConclusion(),
                paper.getSummaryValue(),
                TextNormalizer.splitKeywords(paper.getKeywords()),
                paper.getParseStatus(),
                paper.getOriginalFileName(),
                paper.getFilePath(),
                paper.getChunks().size(),
                outboundCount,
                inboundCount,
                references,
                latestTask == null ? null : toTask(latestTask),
                paper.getUpdatedAt()
        );
    }

    public static ParseTaskResponse toTask(ParseTask task) {
        return new ParseTaskResponse(
                task.getId(),
                task.getPaper().getId(),
                task.getPaper().getTitle(),
                task.getPaper().getOriginalFileName(),
                task.getTaskType(),
                task.getStatus(),
                task.getQueueOrder(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }

    public static QaSessionSummaryResponse toQaSessionSummary(QaSession session) {
        Paper paper = session.getPaper();
        return new QaSessionSummaryResponse(
                session.getId(),
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                session.getTitle(),
                session.getQuestion(),
                session.getScope(),
                session.getSummary(),
                session.isArchived(),
                0,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public static QaSessionDetailResponse toQaSessionDetail(QaSession session) {
        Paper paper = session.getPaper();
        return new QaSessionDetailResponse(
                session.getId(),
                paper == null ? null : paper.getId(),
                paper == null ? null : paper.getTitle(),
                session.getTitle(),
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
                List.of(),
                session.getEvidences().stream()
                        .map(PaperMapper::toQaEvidence)
                        .toList()
        );
    }

    public static QaSessionDetailResponse.Evidence toQaEvidence(QaEvidence evidence) {
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

    public static ExternalPaperResponse toExternalPaper(ExternalPaper externalPaper) {
        return new ExternalPaperResponse(
                externalPaper.getId(),
                externalPaper.getTitle(),
                externalPaper.getAuthors(),
                externalPaper.getYear(),
                externalPaper.getAbstractText(),
                externalPaper.getUrl(),
                externalPaper.getSource(),
                externalPaper.getStatus(),
                externalPaper.getCreatedAt()
        );
    }

    public static AdminRelationResponse toAdminRelation(PaperRelation relation) {
        return new AdminRelationResponse(
                relation.getId(),
                relation.getSourcePaper().getId(),
                relation.getSourcePaper().getTitle(),
                relation.getTargetPaper().getId(),
                relation.getTargetPaper().getTitle(),
                relation.getRelationType(),
                relation.getConfidence(),
                relation.getEvidence()
        );
    }
}

