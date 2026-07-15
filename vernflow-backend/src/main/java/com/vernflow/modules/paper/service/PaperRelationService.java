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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaperRelationService {

    private final PaperRepository paperRepository;
    private final PaperRelationRepository paperRelationRepository;
    private final BigramSimilarityCalculator similarityCalculator;

    public PaperRelationService(
            PaperRepository paperRepository,
            PaperRelationRepository paperRelationRepository,
            BigramSimilarityCalculator similarityCalculator
    ) {
        this.paperRepository = paperRepository;
        this.paperRelationRepository = paperRelationRepository;
        this.similarityCalculator = similarityCalculator;
    }

    @Transactional
    public void rebuildAllRelations() {
        List<Paper> papers = paperRepository.findAll();
        paperRelationRepository.deleteAllInBatch();

        List<PaperRelation> relations = new ArrayList<>();
        Set<String> relation_keys = new HashSet<>();

        for (Paper paper : papers) {
            for (PaperReference reference : paper.getReferences()) {
                ReferenceMatch match = findBestMatch(reference, paper, papers);
                reference.updateMatch(match.matchedPaperId(), match.matchScore());
                if (match.matchedPaper() != null) {
                    String relation_key = paper.getId() + "->" + match.matchedPaper().getId();
                    if (relation_keys.add(relation_key)) {
                        relations.add(PaperRelation.create(
                                paper,
                                match.matchedPaper(),
                                RelationType.CITES,
                                match.matchScore(),
                                reference.getRawReference()
                        ));
                    }
                }
            }
        }

        paperRelationRepository.saveAll(relations);
    }

    private ReferenceMatch findBestMatch(PaperReference reference, Paper owner, List<Paper> candidatePapers) {
        if (!StringUtils.hasText(reference.getRefTitle())) {
            return ReferenceMatch.unmatched(0.0);
        }

        String normalized_reference_title = TextNormalizer.normalizeTitle(reference.getRefTitle());
        Paper best_paper = null;
        double best_score = 0.0;
        double second_score = 0.0;

        for (Paper candidate : candidatePapers) {
            if (candidate.getId().equals(owner.getId())) {
                continue;
            }
            String normalized_candidate_title = TextNormalizer.normalizeTitle(candidate.getTitle());
            if (normalized_candidate_title.isEmpty()) {
                continue;
            }

            double score = similarityCalculator.calculate(normalized_reference_title, normalized_candidate_title);
            if (reference.getRefYear() != null && reference.getRefYear().equals(candidate.getPublicationYear())) {
                score = Math.min(1.0, score + 0.04);
            }
            if (score > best_score) {
                second_score = best_score;
                best_score = score;
                best_paper = candidate;
            } else if (score > second_score) {
                second_score = score;
            }
        }

        boolean year_matched = best_paper != null
                && reference.getRefYear() != null
                && reference.getRefYear().equals(best_paper.getPublicationYear());
        boolean score_enough = best_score >= 0.92 || (year_matched && best_score >= 0.80);
        boolean unambiguous = best_score - second_score >= 0.03 || best_score >= 0.97;

        if (best_paper != null && score_enough && unambiguous) {
            return ReferenceMatch.matched(best_paper, best_score);
        }
        return ReferenceMatch.unmatched(best_score);
    }

    private record ReferenceMatch(Paper matchedPaper, Double matchScore) {

        private static ReferenceMatch matched(Paper matchedPaper, Double matchScore) {
            return new ReferenceMatch(matchedPaper, matchScore);
        }

        private static ReferenceMatch unmatched(Double matchScore) {
            return new ReferenceMatch(null, matchScore);
        }

        private Long matchedPaperId() {
            return matchedPaper == null ? null : matchedPaper.getId();
        }
    }
}

