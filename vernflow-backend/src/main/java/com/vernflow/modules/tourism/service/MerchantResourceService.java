package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.MerchantResourceCreateRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MerchantResourceService {

    private static final String MERCHANT_SOURCE = "商家自主维护";

    private final TourismResourceRepository tourismResourceRepository;
    private final ObjectMapper objectMapper;

    public MerchantResourceService(
            TourismResourceRepository tourismResourceRepository,
            ObjectMapper objectMapper
    ) {
        this.tourismResourceRepository = tourismResourceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantResourceResponse> listResources() {
        return tourismResourceRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MerchantResourceResponse createResource(MerchantResourceCreateRequest request) {
        String city = request.city().trim();
        String name = request.name().trim();
        if (tourismResourceRepository.existsByCityIgnoreCaseAndNameIgnoreCase(city, name)) {
            throw new BusinessException("同一城市下已存在同名文旅资源。");
        }

        List<String> tags = sanitizeTags(request.tags());
        TourismResource resource = TourismResource.draft(
                city,
                name,
                request.category(),
                request.district().trim(),
                StringUtils.hasText(request.summary())
                        ? request.summary().trim()
                        : "商家已创建基础资源，详细体验说明等待补充。",
                writeList(tags),
                writeList(List.of()),
                null,
                MERCHANT_SOURCE,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        return toResponse(tourismResourceRepository.save(resource));
    }

    @Transactional
    public MerchantResourceResponse changeStatus(
            Long resourceId,
            TourismResource.PublicationStatus status
    ) {
        TourismResource resource = tourismResourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException("文旅资源不存在。"));
        if (status == TourismResource.PublicationStatus.PUBLISHED && qualityScore(resource) < 70) {
            throw new BusinessException("资源完整度不足，暂时不能上线。请先补充简介、标签和来源信息。");
        }
        resource.changeStatus(status);
        return toResponse(tourismResourceRepository.save(resource));
    }

    private MerchantResourceResponse toResponse(TourismResource resource) {
        return new MerchantResourceResponse(
                resource.getId(),
                resource.getCity(),
                resource.getName(),
                resource.getCategory().name(),
                categoryLabel(resource.getCategory()),
                resource.getDistrict(),
                resource.getSummary(),
                readList(resource.getTagsJson()),
                resource.getSourceName(),
                resource.getSourceUrl(),
                resource.getOpenHours(),
                resource.getPriceMin(),
                resource.getPriceMax(),
                resource.getStatus().name(),
                statusLabel(resource.getStatus()),
                qualityScore(resource),
                resource.getUpdatedAt()
        );
    }

    private int qualityScore(TourismResource resource) {
        int score = 45;
        if (StringUtils.hasText(resource.getSummary())) {
            score += 15;
        }
        if (!readList(resource.getTagsJson()).isEmpty()) {
            score += 10;
        }
        if (StringUtils.hasText(resource.getDistrict())) {
            score += 5;
        }
        if (StringUtils.hasText(resource.getSourceName())) {
            score += 5;
        }
        if (StringUtils.hasText(resource.getOpenHours())) {
            score += 8;
        }
        if (resource.getPriceMax() != null && resource.getPriceMax().signum() > 0) {
            score += 5;
        }
        if (StringUtils.hasText(resource.getSourceUrl())) {
            score += 7;
        }
        return Math.min(score, 100);
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) {
            return List.of("新建资源");
        }
        List<String> sanitized = tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        return sanitized.isEmpty() ? List.of("新建资源") : sanitized;
    }

    private String categoryLabel(TourismResource.Category category) {
        return switch (category) {
            case SCENIC -> "自然景观";
            case CULTURE -> "文化场馆";
            case EXPERIENCE -> "非遗体验";
            case FOOD -> "本地餐饮";
            case LODGING -> "住宿";
            case LEISURE -> "城市休闲";
        };
    }

    private String statusLabel(TourismResource.PublicationStatus status) {
        return switch (status) {
            case DRAFT -> "待完善";
            case PUBLISHED -> "已上线";
            case INACTIVE -> "已停用";
        };
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tourism resource tags.", exception);
        }
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
