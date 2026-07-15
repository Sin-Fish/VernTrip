package com.vernflow.modules.paper.service;

import com.vernflow.common.response.PageResponse;
import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.common.util.BigramSimilarityCalculator;
import com.vernflow.common.util.TextNormalizer;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.PaperMapper;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.worker.PaperParsingWorker;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperQueryService {

    private final PaperRepository paperRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final PaperRelationRepository paperRelationRepository;
    private final ParseTaskRepository parseTaskRepository;

    public PaperQueryService(
            PaperRepository paperRepository,
            PaperReferenceRepository paperReferenceRepository,
            PaperRelationRepository paperRelationRepository,
            ParseTaskRepository parseTaskRepository
    ) {
        this.paperRepository = paperRepository;
        this.paperReferenceRepository = paperReferenceRepository;
        this.paperRelationRepository = paperRelationRepository;
        this.parseTaskRepository = parseTaskRepository;
    }

    public PageResponse<PaperItemResponse> listPapers(String keyword, Integer year, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(paperRepository.findAll(buildPaperSpecification(keyword, year), pageRequest).map(PaperMapper::toItem));
    }

    @Transactional(readOnly = true)
    public PaperDetailResponse getPaperDetail(Long paperId) {
        Paper paper = loadPaper(paperId);
        List<PaperReference> references = paperReferenceRepository.findByPaperIdOrderByIdAsc(paperId);
        Map<Long, String> matchedTitles = buildMatchedTitleMap(references);
        long outboundCount = paperRelationRepository.countBySourcePaperId(paperId);
        long inboundCount = paperRelationRepository.countByTargetPaperId(paperId);
        ParseTask latestTask = parseTaskRepository.findTopByPaperIdOrderByCreatedAtDesc(paperId).orElse(null);
        return PaperMapper.toDetail(
                paper,
                references.stream().map(reference -> PaperMapper.toReference(reference, matchedTitles)).toList(),
                outboundCount,
                inboundCount,
                latestTask
        );
    }

    @Transactional(readOnly = true)
    public PaperGraphResponse getPaperGraph(Long paperId) {
        Paper centerPaper = loadPaper(paperId);
        List<PaperRelation> relations = paperRelationRepository.findBySourcePaperIdOrTargetPaperId(paperId, paperId);
        Map<Long, Paper> paperMap = new HashMap<>();
        paperMap.put(centerPaper.getId(), centerPaper);
        for (PaperRelation relation : relations) {
            paperMap.put(relation.getSourcePaper().getId(), relation.getSourcePaper());
            paperMap.put(relation.getTargetPaper().getId(), relation.getTargetPaper());
        }

        List<PaperGraphResponse.Node> nodes = paperMap.values().stream()
                .map(paper -> new PaperGraphResponse.Node(
                        paper.getId(),
                        paper.getTitle(),
                        paper.getPublicationYear(),
                        paper.getId().equals(centerPaper.getId())
                ))
                .sorted(Comparator
                        .comparing(PaperGraphResponse.Node::highlighted).reversed()
                        .thenComparing(node -> node.title().toLowerCase()))
                .toList();

        List<PaperGraphResponse.Edge> edges = relations.stream()
                .map(relation -> new PaperGraphResponse.Edge(
                        relation.getSourcePaper().getId(),
                        relation.getTargetPaper().getId(),
                        relation.getRelationType(),
                        relation.getConfidence()
                ))
                .toList();

        return new PaperGraphResponse(centerPaper.getId(), nodes, edges);
    }

    @Transactional(readOnly = true)
    public PageResponse<ParseTaskResponse> listTasks(int page, int size) {
        List<ParseTask> sortedTasks = parseTaskRepository.findAll().stream()
                .sorted(taskComparator())
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, sortedTasks.size());
        int toIndex = Math.min(fromIndex + safeSize, sortedTasks.size());
        PageImpl<ParseTask> taskPage = new PageImpl<>(
                sortedTasks.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                sortedTasks.size()
        );
        return PageResponse.from(taskPage.map(PaperMapper::toTask));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRelationResponse> listRelations(Long paperId, int page, int size) {
        List<PaperRelation> sortedRelations = (paperId == null
                ? paperRelationRepository.findAll()
                : paperRelationRepository.findAllBySourcePaperIdOrTargetPaperIdOrderByConfidenceDesc(paperId, paperId))
                .stream()
                .sorted(Comparator
                        .comparing(PaperRelation::getConfidence, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(relation -> relation.getSourcePaper().getTitle().toLowerCase())
                        .thenComparing(relation -> relation.getTargetPaper().getTitle().toLowerCase()))
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, sortedRelations.size());
        int toIndex = Math.min(fromIndex + safeSize, sortedRelations.size());
        PageImpl<PaperRelation> relationPage = new PageImpl<>(
                sortedRelations.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                sortedRelations.size()
        );
        return PageResponse.from(relationPage.map(PaperMapper::toAdminRelation));
    }

    @Transactional(readOnly = true)
    public PaperFileDescriptor getPaperFile(Long paperId) {
        Paper paper = loadPaper(paperId);
        Path filePath = Path.of(paper.getFilePath()).normalize().toAbsolutePath();
        if (!Files.exists(filePath)) {
            throw new BusinessException("未找到对应原文文件");
        }
        return new PaperFileDescriptor(paper.getOriginalFileName(), filePath);
    }

    private Specification<Paper> buildPaperSpecification(String keyword, Integer year) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("authors")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("venue")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("keywords")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("originalFileName")), likeKeyword)
                ));
            }
            if (year != null) {
                predicates.add(criteriaBuilder.equal(root.get("publicationYear"), year));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, String> buildMatchedTitleMap(List<PaperReference> references) {
        List<Long> matchedIds = references.stream()
                .map(PaperReference::getMatchedPaperId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> matchedTitles = new HashMap<>();
        paperRepository.findAllById(matchedIds)
                .forEach(paper -> matchedTitles.put(paper.getId(), paper.getTitle()));
        return matchedTitles;
    }

    private Paper loadPaper(Long paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new BusinessException("未找到对应论文"));
    }

    private Comparator<ParseTask> taskComparator() {
        return Comparator
                .comparingInt(this::taskStatusPriority)
                .thenComparing(task -> isQueueManaged(task) ? task.getQueueOrder() : Long.MAX_VALUE)
                .thenComparing(ParseTask::getUpdatedAt, Comparator.reverseOrder())
                .thenComparing(ParseTask::getCreatedAt, Comparator.reverseOrder());
    }

    private int taskStatusPriority(ParseTask task) {
        return switch (task.getStatus()) {
            case PROCESSING -> 0;
            case PENDING -> 1;
            case PAUSED -> 2;
            case FAILED -> 3;
            case SUCCESS -> 4;
        };
    }

    private boolean isQueueManaged(ParseTask task) {
        return task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.PAUSED;
    }
}

