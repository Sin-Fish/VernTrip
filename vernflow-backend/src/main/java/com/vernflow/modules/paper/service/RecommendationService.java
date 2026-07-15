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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int DEFAULT_SEED_LIMIT = 24;

    private final ExternalPaperRepository externalPaperRepository;
    private final PaperReferenceRepository paperReferenceRepository;

    public RecommendationService(
            ExternalPaperRepository externalPaperRepository,
            PaperReferenceRepository paperReferenceRepository
    ) {
        this.externalPaperRepository = externalPaperRepository;
        this.paperReferenceRepository = paperReferenceRepository;
    }

    @Transactional
    public List<ExternalPaperResponse> listRandomRecommendations(int size) {
        ensureSeedRecommendations();
        List<ExternalPaper> candidates = new ArrayList<>(externalPaperRepository.findAllByStatusOrderByCreatedAtDesc(ExternalPaperStatus.PENDING));
        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(Math.max(size, 1))
                .map(PaperMapper::toExternalPaper)
                .toList();
    }

    @Transactional
    public ExternalPaperResponse importRecommendation(Long recommendationId) {
        ExternalPaper externalPaper = loadExternalPaper(recommendationId);
        externalPaper.markImported();
        return PaperMapper.toExternalPaper(externalPaper);
    }

    @Transactional
    public ExternalPaperResponse ignoreRecommendation(Long recommendationId) {
        ExternalPaper externalPaper = loadExternalPaper(recommendationId);
        externalPaper.markIgnored();
        return PaperMapper.toExternalPaper(externalPaper);
    }

    private void ensureSeedRecommendations() {
        if (!externalPaperRepository.findAllByStatusOrderByCreatedAtDesc(ExternalPaperStatus.PENDING).isEmpty()) {
            return;
        }

        Set<String> existingKeys = new LinkedHashSet<>();
        externalPaperRepository.findAll().forEach(paper -> existingKeys.add(candidateKey(paper.getTitle(), paper.getYear())));

        List<ExternalPaper> seeds = new ArrayList<>();
        for (PaperReference reference : paperReferenceRepository.findAll()) {
            if (reference.getMatchedPaperId() != null || !StringUtils.hasText(reference.getRefTitle())) {
                continue;
            }
            String key = candidateKey(reference.getRefTitle(), reference.getRefYear());
            if (!existingKeys.add(key)) {
                continue;
            }
            seeds.add(ExternalPaper.create(
                    reference.getRefTitle().trim(),
                    reference.getRefAuthors(),
                    reference.getRefYear(),
                    null,
                    null,
                    "reference_seed"
            ));
            if (seeds.size() >= DEFAULT_SEED_LIMIT) {
                break;
            }
        }

        if (!seeds.isEmpty()) {
            externalPaperRepository.saveAll(seeds);
        }
    }

    private ExternalPaper loadExternalPaper(Long recommendationId) {
        return externalPaperRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException("未找到对应推荐记录: " + recommendationId));
    }

    private String candidateKey(String title, Integer year) {
        return TextNormalizer.normalizeTitle(title) + "#" + (year == null ? "" : year);
    }
}

