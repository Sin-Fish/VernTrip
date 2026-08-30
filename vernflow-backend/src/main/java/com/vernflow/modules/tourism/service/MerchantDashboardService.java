package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismProduct;
import com.vernflow.modules.tourism.domain.TripRequest;
import com.vernflow.modules.tourism.dto.MerchantDashboardResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;
import com.vernflow.modules.tourism.repository.TourismProductRepository;
import com.vernflow.modules.tourism.repository.TravelPlanItemRepository;
import com.vernflow.modules.tourism.repository.TravelPlanRepository;
import com.vernflow.modules.tourism.repository.TripRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MerchantDashboardService {

    private final MerchantResourceService resourceService;
    private final TourismProductRepository productRepository;
    private final TravelPlanRepository planRepository;
    private final TravelPlanItemRepository planItemRepository;
    private final TripRequestRepository tripRequestRepository;
    private final ObjectMapper objectMapper;

    public MerchantDashboardService(
            MerchantResourceService resourceService,
            TourismProductRepository productRepository,
            TravelPlanRepository planRepository,
            TravelPlanItemRepository planItemRepository,
            TripRequestRepository tripRequestRepository,
            ObjectMapper objectMapper
    ) {
        this.resourceService = resourceService;
        this.productRepository = productRepository;
        this.planRepository = planRepository;
        this.planItemRepository = planItemRepository;
        this.tripRequestRepository = tripRequestRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MerchantDashboardResponse dashboard(Long merchantUserId) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<MerchantResourceResponse> resources = resourceService.listResources();
        List<TripRequest> recentRequests = tripRequestRepository
                .findAllByCreatedAtAfterOrderByCreatedAtDesc(thirtyDaysAgo);

        int publishedCount = (int) resources.stream().filter(item -> "PUBLISHED".equals(item.status())).count();
        int recommendableCount = (int) resources.stream()
                .filter(item -> "PUBLISHED".equals(item.status()) && !"SOLD_OUT".equals(item.availabilityStatus()))
                .count();
        int averageQuality = (int) Math.round(resources.stream()
                .mapToInt(MerchantResourceResponse::qualityScore)
                .average()
                .orElse(0));
        int soldOutCount = (int) resources.stream()
                .filter(item -> "SOLD_OUT".equals(item.availabilityStatus()))
                .count();
        int limitedCount = (int) resources.stream()
                .filter(item -> "LIMITED".equals(item.availabilityStatus()))
                .count();

        List<MerchantDashboardResponse.InterestTrend> interests = rankedValues(
                recentRequests.stream().flatMap(request -> readList(request.getInterestsJson()).stream()).toList(),
                recentRequests.size()
        ).stream().map(item -> new MerchantDashboardResponse.InterestTrend(item.label(), item.count(), item.share()))
                .toList();
        List<MerchantDashboardResponse.AudienceSegment> audiences = rankedValues(
                recentRequests.stream().map(TripRequest::getTravelerType).filter(StringUtils::hasText).toList(),
                recentRequests.size()
        ).stream().map(item -> new MerchantDashboardResponse.AudienceSegment(item.label(), item.count(), item.share()))
                .toList();

        BigDecimal averageBudget = recentRequests.isEmpty()
                ? BigDecimal.ZERO
                : recentRequests.stream()
                        .map(TripRequest::getBudget)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(recentRequests.size()), 0, RoundingMode.HALF_UP);
        double averageDays = recentRequests.stream().mapToInt(TripRequest::getDays).average().orElse(0);

        Map<Long, MerchantResourceResponse> resourcesById = resources.stream()
                .collect(Collectors.toMap(MerchantResourceResponse::id, Function.identity()));
        List<MerchantDashboardResponse.ResourceCitation> citations = planItemRepository
                .countResourceCitationsSince(sevenDaysAgo).stream()
                .map(row -> {
                    Long resourceId = (Long) row[0];
                    MerchantResourceResponse resource = resourcesById.get(resourceId);
                    return new MerchantDashboardResponse.ResourceCitation(
                            resourceId,
                            resource == null ? "已移除资源" : resource.name(),
                            ((Number) row[1]).longValue()
                    );
                })
                .limit(6)
                .toList();

        List<MerchantDashboardResponse.OperationTask> tasks = buildTasks(resources);
        String advice = operationAdvice(interests, soldOutCount, limitedCount, publishedCount);
        return new MerchantDashboardResponse(
                resources.size(),
                publishedCount,
                recommendableCount,
                averageQuality,
                soldOutCount,
                limitedCount,
                productRepository.countByMerchantUserIdAndStatus(merchantUserId, TourismProduct.Status.DRAFT)
                        + productRepository.countByMerchantUserIdAndStatus(merchantUserId, TourismProduct.Status.PUBLISHED)
                        + productRepository.countByMerchantUserIdAndStatus(merchantUserId, TourismProduct.Status.INACTIVE),
                productRepository.countByMerchantUserIdAndStatus(merchantUserId, TourismProduct.Status.PUBLISHED),
                planRepository.countByCreatedAtAfter(sevenDaysAgo),
                recentRequests.size(),
                averageBudget,
                Math.round(averageDays * 10.0) / 10.0,
                interests,
                audiences,
                citations,
                tasks,
                advice
        );
    }

    private List<RankedValue> rankedValues(List<String> values, int totalRequests) {
        if (values.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = values.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        long denominator = Math.max(1, totalRequests);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(6)
                .map(entry -> new RankedValue(
                        entry.getKey(),
                        entry.getValue(),
                        (int) Math.min(100, Math.round(entry.getValue() * 100.0 / denominator))
                ))
                .toList();
    }

    private List<MerchantDashboardResponse.OperationTask> buildTasks(List<MerchantResourceResponse> resources) {
        List<MerchantDashboardResponse.OperationTask> tasks = new ArrayList<>();
        resources.stream().filter(item -> "SOLD_OUT".equals(item.availabilityStatus())).limit(2).forEach(item ->
                tasks.add(new MerchantDashboardResponse.OperationTask(
                        "CAPACITY", "更新已约满资源", item.name() + "当前余量为 0，已退出推荐池。", "优先处理", item.id()
                )));
        resources.stream().filter(item -> "LIMITED".equals(item.availabilityStatus())).limit(2).forEach(item ->
                tasks.add(new MerchantDashboardResponse.OperationTask(
                        "CAPACITY", "确认剩余接待能力", item.name() + "余量紧张，请核对是否需要补充场次。", "容量提醒", item.id()
                )));
        resources.stream().filter(item -> "DRAFT".equals(item.status())).limit(2).forEach(item ->
                tasks.add(new MerchantDashboardResponse.OperationTask(
                        "RESOURCE", "完善待发布资源", item.name() + "尚未进入游客推荐池。", "内容完善", item.id()
                )));
        resources.stream().filter(item -> item.qualityScore() < 85).limit(2).forEach(item ->
                tasks.add(new MerchantDashboardResponse.OperationTask(
                        "QUALITY", "补充资源可信信息", item.name() + "当前质量分为 " + item.qualityScore() + "。", "质量校验", item.id()
                )));
        if (tasks.isEmpty()) {
            tasks.add(new MerchantDashboardResponse.OperationTask(
                    "HEALTHY", "资源状态良好", "当前没有约满、低余量或待完善资源。", "无需处理", null
            ));
        }
        return tasks.stream().limit(5).toList();
    }

    private String operationAdvice(
            List<MerchantDashboardResponse.InterestTrend> interests,
            int soldOutCount,
            int limitedCount,
            int publishedCount
    ) {
        if (soldOutCount > 0) {
            return "有资源因余量为 0 已退出推荐池，建议先更新接待容量，再继续推广相关产品。";
        }
        if (limitedCount > 0) {
            return "部分预约资源余量紧张，建议核对新增场次，并避免在营销内容中承诺即时可约。";
        }
        if (!interests.isEmpty()) {
            return "近 30 日游客对“" + interests.getFirst().label() + "”关注度最高，可优先围绕该主题组合产品并生成渠道内容。";
        }
        if (publishedCount == 0) {
            return "当前没有已上线资源，请先完善资源信息并发布，再进行产品组合和内容生成。";
        }
        return "资源库已具备推荐条件，后续游客需求将自动汇总为匿名化经营洞察。";
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

    private record RankedValue(String label, long count, int share) {
    }
}
