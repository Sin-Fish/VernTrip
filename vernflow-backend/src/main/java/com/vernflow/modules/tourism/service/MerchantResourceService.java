package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismProduct;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.MerchantResourceCreateRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceUpdateRequest;
import com.vernflow.modules.tourism.repository.TourismPlaceRepository;
import com.vernflow.modules.tourism.repository.TourismProductRepository;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MerchantResourceService {

    private static final String MERCHANT_SOURCE = "商家自主维护";
    private static final String AMAP_SOURCE = "高德 POI（商家选择）";

    private final TourismResourceRepository tourismResourceRepository;
    private final TourismPlaceRepository tourismPlaceRepository;
    private final TourismProductRepository tourismProductRepository;
    private final ObjectMapper objectMapper;

    public MerchantResourceService(
            TourismResourceRepository tourismResourceRepository,
            TourismPlaceRepository tourismPlaceRepository,
            TourismProductRepository tourismProductRepository,
            ObjectMapper objectMapper
    ) {
        this.tourismResourceRepository = tourismResourceRepository;
        this.tourismPlaceRepository = tourismPlaceRepository;
        this.tourismProductRepository = tourismProductRepository;
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
        OperatingDetails operating = operatingDetails(
                request.openHours(),
                request.priceMin(),
                request.priceMax(),
                request.reservationRequired(),
                request.dailyCapacity(),
                request.availableCapacity()
        );
        TourismPlace place = tourismPlaceRepository.findByAmapPoiId(request.amapPoiId().trim())
                .orElseGet(() -> tourismPlaceRepository.save(TourismPlace.verified(
                        city,
                        request.placeName().trim(),
                        request.district().trim(),
                        StringUtils.hasText(request.address()) ? request.address().trim() : request.district().trim(),
                        request.amapPoiId().trim(),
                        request.longitude(),
                        request.latitude(),
                        AMAP_SOURCE
                )));
        TourismResource resource = TourismResource.draft(
                place,
                name,
                request.category(),
                StringUtils.hasText(request.summary())
                        ? request.summary().trim()
                        : "商家已创建基础资源，详细体验说明等待补充。",
                writeList(tags),
                writeList(List.of()),
                MERCHANT_SOURCE,
                null,
                operating.openHours(),
                operating.priceMin(),
                operating.priceMax()
        );
        resource.updateDetails(
                name,
                request.category(),
                resource.getSummary(),
                writeList(tags),
                operating.openHours(),
                operating.priceMin(),
                operating.priceMax(),
                operating.reservationRequired(),
                operating.dailyCapacity(),
                operating.availableCapacity()
        );
        deactivateProductsWhenUnavailable(resource);
        return toResponse(tourismResourceRepository.save(resource));
    }

    @Transactional
    public MerchantResourceResponse updateResource(Long resourceId, MerchantResourceUpdateRequest request) {
        TourismResource resource = tourismResourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException("文旅资源不存在。"));
        String name = request.name().trim();
        if (tourismResourceRepository.existsByCityIgnoreCaseAndNameIgnoreCaseAndIdNot(
                resource.getCity(),
                name,
                resourceId
        )) {
            throw new BusinessException("同一城市下已存在同名文旅资源。");
        }
        OperatingDetails operating = operatingDetails(
                request.openHours(),
                request.priceMin(),
                request.priceMax(),
                request.reservationRequired(),
                request.dailyCapacity(),
                request.availableCapacity()
        );
        resource.updateDetails(
                name,
                request.category(),
                request.summary().trim(),
                writeList(sanitizeTags(request.tags())),
                operating.openHours(),
                operating.priceMin(),
                operating.priceMax(),
                operating.reservationRequired(),
                operating.dailyCapacity(),
                operating.availableCapacity()
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
        deactivateProductsWhenUnavailable(resource);
        return toResponse(tourismResourceRepository.save(resource));
    }

    private void deactivateProductsWhenUnavailable(TourismResource resource) {
        if (resource.isAvailableForPlanning()) {
            return;
        }
        List<TourismProduct> affectedProducts = tourismProductRepository.findAllByResourcesIdAndStatus(
                resource.getId(),
                TourismProduct.Status.PUBLISHED
        );
        affectedProducts.forEach(product -> product.changeStatus(TourismProduct.Status.INACTIVE));
        tourismProductRepository.saveAll(affectedProducts);
    }

    private MerchantResourceResponse toResponse(TourismResource resource) {
        return new MerchantResourceResponse(
                resource.getId(),
                resource.getCity(),
                resource.getName(),
                resource.getCategory().name(),
                categoryLabel(resource.getCategory()),
                resource.getDistrict(),
                resource.getPlace().getId(),
                resource.getPlace().getName(),
                resource.getPlace().getVerificationStatus().name(),
                resource.getAmapPoiId(),
                resource.getAddress(),
                resource.getLongitude(),
                resource.getLatitude(),
                resource.getSummary(),
                readList(resource.getTagsJson()),
                resource.getSourceName(),
                resource.getSourceUrl(),
                resource.getOpenHours(),
                resource.getPriceMin(),
                resource.getPriceMax(),
                resource.isReservationRequired(),
                resource.getDailyCapacity(),
                resource.getAvailableCapacity(),
                availabilityStatus(resource),
                availabilityLabel(resource),
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
        if (resource.getPlace().getVerificationStatus() == TourismPlace.VerificationStatus.VERIFIED
                && resource.getLongitude() != null
                && resource.getLatitude() != null) {
            score += 10;
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

    private OperatingDetails operatingDetails(
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Boolean reservationRequired,
            Integer dailyCapacity,
            Integer availableCapacity
    ) {
        BigDecimal normalizedMin = priceMin == null ? BigDecimal.ZERO : priceMin;
        BigDecimal normalizedMax = priceMax == null ? normalizedMin : priceMax;
        if (normalizedMax.compareTo(normalizedMin) < 0) {
            throw new BusinessException("最高价格不能低于最低价格。");
        }
        boolean requiresReservation = Boolean.TRUE.equals(reservationRequired);
        if (requiresReservation && (dailyCapacity == null || dailyCapacity <= 0 || availableCapacity == null)) {
            throw new BusinessException("需要预约的资源必须填写每日容量和当前余量。");
        }
        if (requiresReservation && availableCapacity > dailyCapacity) {
            throw new BusinessException("当前余量不能超过每日容量。");
        }
        return new OperatingDetails(
                StringUtils.hasText(openHours) ? openHours.trim() : null,
                normalizedMin,
                normalizedMax,
                requiresReservation,
                requiresReservation ? dailyCapacity : null,
                requiresReservation ? availableCapacity : null
        );
    }

    private String availabilityStatus(TourismResource resource) {
        if (!resource.isReservationRequired()) {
            return "DIRECT";
        }
        if (resource.getAvailableCapacity() != null && resource.getAvailableCapacity() == 0) {
            return "SOLD_OUT";
        }
        if (resource.getDailyCapacity() != null
                && resource.getAvailableCapacity() != null
                && resource.getAvailableCapacity() * 4 <= resource.getDailyCapacity()) {
            return "LIMITED";
        }
        return "AVAILABLE";
    }

    private String availabilityLabel(TourismResource resource) {
        return switch (availabilityStatus(resource)) {
            case "SOLD_OUT" -> "已约满";
            case "LIMITED" -> "余量紧张";
            case "AVAILABLE" -> "可预约";
            default -> "无需预约";
        };
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

    private record OperatingDetails(
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax,
            boolean reservationRequired,
            Integer dailyCapacity,
            Integer availableCapacity
    ) {
    }
}
