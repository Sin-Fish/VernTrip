package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.tourism.domain.TourismProduct;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;
import com.vernflow.modules.tourism.dto.MerchantProductUpsertRequest;
import com.vernflow.modules.tourism.repository.TourismProductRepository;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantProductService {

    private final TourismProductRepository productRepository;
    private final TourismResourceRepository resourceRepository;
    private final ObjectMapper objectMapper;

    public MerchantProductService(
            TourismProductRepository productRepository,
            TourismResourceRepository resourceRepository,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.resourceRepository = resourceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantProductResponse> listProducts(Long merchantUserId) {
        return productRepository.findAllByMerchantUserIdOrderByUpdatedAtDesc(merchantUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantProductResponse getProduct(Long merchantUserId, Long productId) {
        return toResponse(requireProduct(merchantUserId, productId));
    }

    @Transactional
    public MerchantProductResponse createProduct(Long merchantUserId, MerchantProductUpsertRequest request) {
        String name = request.name().trim();
        if (productRepository.existsByMerchantUserIdAndNameIgnoreCase(merchantUserId, name)) {
            throw new BusinessException("当前商家已存在同名旅游产品。");
        }
        List<TourismResource> resources = requireResources(request.resourceIds());
        TourismProduct product = TourismProduct.draft(
                merchantUserId,
                name,
                request.targetAudience().trim(),
                request.durationDays(),
                request.price(),
                request.description().trim(),
                writeList(sanitizeHighlights(request.highlights())),
                resources
        );
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public MerchantProductResponse updateProduct(
            Long merchantUserId,
            Long productId,
            MerchantProductUpsertRequest request
    ) {
        TourismProduct product = requireProduct(merchantUserId, productId);
        String name = request.name().trim();
        if (productRepository.existsByMerchantUserIdAndNameIgnoreCaseAndIdNot(merchantUserId, name, productId)) {
            throw new BusinessException("当前商家已存在同名旅游产品。");
        }
        product.updateDetails(
                name,
                request.targetAudience().trim(),
                request.durationDays(),
                request.price(),
                request.description().trim(),
                writeList(sanitizeHighlights(request.highlights())),
                requireResources(request.resourceIds())
        );
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public MerchantProductResponse changeStatus(
            Long merchantUserId,
            Long productId,
            TourismProduct.Status status
    ) {
        TourismProduct product = requireProduct(merchantUserId, productId);
        if (status == TourismProduct.Status.PUBLISHED) {
            List<String> unavailableNames = product.getResources().stream()
                    .filter(resource -> !resource.isAvailableForPlanning())
                    .map(TourismResource::getName)
                    .toList();
            if (!unavailableNames.isEmpty()) {
                throw new BusinessException("产品包含未上线或不可预约资源：" + String.join("、", unavailableNames));
            }
        }
        product.changeStatus(status);
        return toResponse(productRepository.save(product));
    }

    private TourismProduct requireProduct(Long merchantUserId, Long productId) {
        return productRepository.findByIdAndMerchantUserId(productId, merchantUserId)
                .orElseThrow(() -> new BusinessException("旅游产品不存在或不属于当前商家。"));
    }

    private List<TourismResource> requireResources(List<Long> resourceIds) {
        List<Long> distinctIds = resourceIds.stream().distinct().toList();
        if (distinctIds.size() < 2) {
            throw new BusinessException("旅游产品至少需要组合两项文旅资源。");
        }
        Map<Long, TourismResource> found = new LinkedHashMap<>();
        resourceRepository.findAllById(distinctIds).forEach(resource -> found.put(resource.getId(), resource));
        if (found.size() != distinctIds.size()) {
            throw new BusinessException("产品包含不存在的文旅资源。");
        }
        return distinctIds.stream().map(found::get).toList();
    }

    private MerchantProductResponse toResponse(TourismProduct product) {
        List<MerchantProductResponse.ResourceSummary> resources = product.getResources().stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .map(resource -> new MerchantProductResponse.ResourceSummary(
                        resource.getId(),
                        resource.getName(),
                        resource.getCategory().name(),
                        categoryLabel(resource.getCategory()),
                        resource.getStatus().name(),
                        resource.isAvailableForPlanning()
                ))
                .toList();
        return new MerchantProductResponse(
                product.getId(),
                product.getName(),
                product.getTargetAudience(),
                product.getDurationDays(),
                product.getPrice(),
                product.getDescription(),
                readList(product.getHighlightsJson()),
                resources,
                product.getStatus().name(),
                statusLabel(product.getStatus()),
                product.getUpdatedAt()
        );
    }

    private List<String> sanitizeHighlights(List<String> highlights) {
        if (highlights == null) {
            return List.of();
        }
        return highlights.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tourism product highlights.", exception);
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
            return new ArrayList<>();
        }
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

    private String statusLabel(TourismProduct.Status status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PUBLISHED -> "已发布";
            case INACTIVE -> "已停用";
        };
    }
}
