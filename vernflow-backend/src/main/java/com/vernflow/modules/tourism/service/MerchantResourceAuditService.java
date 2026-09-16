package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.dto.MerchantResourceAuditResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceCreateRequest;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MerchantResourceAuditService {

    private static final String AUDIT_INSTRUCTIONS = """
            你是 VernTrip 商家资源上架审核助手。只能使用输入 JSON 中的事实，不得补充奖项、服务承诺、交通方式、适宜人群或未提供的经营信息。
            请将资源整理为游客可读的简短中文简介和 3 至 6 个检索标签，并给出最多 3 条可执行的资料完善建议。
            严格输出 JSON，不要使用 Markdown。格式为：
            {"summary":"不超过120字","tags":["标签"],"suggestions":["建议"]}
            """;

    private final OpenClawProperties openClawProperties;
    private final OpenClawSessionContextService sessionContextService;
    private final OpenClawGatewayClient gatewayClient;
    private final MerchantResourceService resourceService;
    private final ObjectMapper objectMapper;

    public MerchantResourceAuditService(
            OpenClawProperties openClawProperties,
            OpenClawSessionContextService sessionContextService,
            OpenClawGatewayClient gatewayClient,
            MerchantResourceService resourceService,
            ObjectMapper objectMapper
    ) {
        this.openClawProperties = openClawProperties;
        this.sessionContextService = sessionContextService;
        this.gatewayClient = gatewayClient;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
    }

    public MerchantResourceAuditResponse audit(Long merchantUserId, MerchantResourceCreateRequest request) {
        List<String> currentTags = sanitizeTags(request.tags());
        DraftEnhancement enhancement = localEnhancement(request, currentTags);
        String engine = "LOCAL_RULES";

        if (openClawProperties.primaryEnabled()) {
            try {
                OpenClawSessionContextService.BoundSession session = sessionContextService.bind(
                        merchantUserId,
                        null,
                        false,
                        List.of()
                );
                OpenClawGatewayClient.ShadowResponse response = gatewayClient.createResponse(
                        session.sessionKey(),
                        buildPrompt(request),
                        AUDIT_INSTRUCTIONS
                );
                enhancement = parseEnhancement(response.text(), enhancement);
                engine = "OPENCLAW";
            } catch (OpenClawGatewayClient.GatewayCallException | JsonProcessingException exception) {
                // Keep the merchant workflow available and identify the fallback in the response.
            }
        }

        int currentScore = resourceService.previewQualityScore(request, request.summary(), currentTags);
        int projectedScore = resourceService.previewQualityScore(
                request,
                enhancement.summary(),
                enhancement.tags()
        );
        boolean soldOut = Boolean.TRUE.equals(request.reservationRequired())
                && request.availableCapacity() != null
                && request.availableCapacity() == 0;
        boolean recommendable = projectedScore >= 70 && !soldOut;
        List<MerchantResourceAuditResponse.AuditCheck> checks = buildChecks(
                request,
                currentTags,
                enhancement,
                soldOut
        );
        List<String> suggestions = mergeSuggestions(request, currentTags, enhancement.suggestions(), soldOut);
        String rationale = recommendable
                ? "真实地点已核验，补全后预计达到 %d 分；发布后可进入%s游客规划候选池。"
                        .formatted(projectedScore, request.city().trim())
                : soldOut
                        ? "资源资料已完成核验，但当前余量为 0，暂不进入游客规划候选池。"
                        : "资源仍需补齐关键经营信息，达到 70 分后方可进入游客规划候选池。";

        return new MerchantResourceAuditResponse(
                currentScore,
                projectedScore,
                recommendable,
                enhancement.summary(),
                enhancement.tags(),
                checks,
                suggestions,
                rationale,
                engine,
                LocalDateTime.now()
        );
    }

    private String buildPrompt(MerchantResourceCreateRequest request) throws JsonProcessingException {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("city", request.city());
        facts.put("name", request.name());
        facts.put("category", categoryLabel(request));
        facts.put("district", request.district());
        facts.put("placeName", request.placeName());
        facts.put("address", request.address());
        facts.put("summary", request.summary());
        facts.put("tags", request.tags());
        facts.put("openHours", request.openHours());
        facts.put("priceMin", request.priceMin());
        facts.put("priceMax", request.priceMax());
        facts.put("reservationRequired", request.reservationRequired());
        facts.put("dailyCapacity", request.dailyCapacity());
        facts.put("availableCapacity", request.availableCapacity());
        return "请审核并整理这份已绑定高德 POI 的资源草稿：\n" + objectMapper.writeValueAsString(facts);
    }

    private DraftEnhancement parseEnhancement(String responseText, DraftEnhancement fallback)
            throws JsonProcessingException {
        String normalized = responseText == null ? "" : responseText.trim();
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart < 0 || objectEnd <= objectStart) {
            throw new JsonProcessingException("OpenClaw audit response did not contain JSON.") {
            };
        }
        JsonNode root = objectMapper.readTree(normalized.substring(objectStart, objectEnd + 1));
        String summary = cleanText(root.path("summary").asText(), 240);
        List<String> tags = readTextArray(root.path("tags"), 6, 40);
        List<String> suggestions = readTextArray(root.path("suggestions"), 3, 120);
        return new DraftEnhancement(
                StringUtils.hasText(summary) ? summary : fallback.summary(),
                tags.isEmpty() ? fallback.tags() : tags,
                suggestions.isEmpty() ? fallback.suggestions() : suggestions
        );
    }

    private DraftEnhancement localEnhancement(MerchantResourceCreateRequest request, List<String> currentTags) {
        String summary = StringUtils.hasText(request.summary())
                ? request.summary().trim()
                : "%s位于%s，在%s提供%s相关内容。开放时间、价格与预约余量以商家实时维护信息为准。"
                        .formatted(
                                request.name().trim(),
                                request.district().trim(),
                                request.placeName().trim(),
                                categoryLabel(request)
                        );
        LinkedHashSet<String> tags = new LinkedHashSet<>(currentTags);
        tags.add(categoryLabel(request));
        tags.add(request.district().trim());
        tags.add(Boolean.TRUE.equals(request.reservationRequired()) ? "需预约" : "无需预约");
        return new DraftEnhancement(summary, tags.stream().limit(6).toList(), List.of());
    }

    private List<MerchantResourceAuditResponse.AuditCheck> buildChecks(
            MerchantResourceCreateRequest request,
            List<String> currentTags,
            DraftEnhancement enhancement,
            boolean soldOut
    ) {
        boolean contentReady = StringUtils.hasText(request.summary()) && currentTags.size() >= 2;
        boolean operatingReady = StringUtils.hasText(request.openHours());
        return List.of(
                new MerchantResourceAuditResponse.AuditCheck(
                        "LOCATION",
                        "真实地点",
                        "PASS",
                        "高德 POI 与经纬度已绑定，可用于地图定位和路线规划。"
                ),
                new MerchantResourceAuditResponse.AuditCheck(
                        "CONTENT",
                        "游客可读内容",
                        contentReady ? "PASS" : "IMPROVED",
                        contentReady
                                ? "简介与检索标签完整。"
                                : "已生成简介与 %d 个检索标签，等待商家确认。".formatted(enhancement.tags().size())
                ),
                new MerchantResourceAuditResponse.AuditCheck(
                        "OPERATING",
                        "经营信息",
                        operatingReady ? "PASS" : "WARN",
                        operatingReady ? "开放时间和价格信息可参与行程编排。" : "建议补充开放时间，避免生成不可执行行程。"
                ),
                new MerchantResourceAuditResponse.AuditCheck(
                        "AVAILABILITY",
                        "推荐资格",
                        soldOut ? "WARN" : "PASS",
                        soldOut ? "当前余量为 0，发布后也会自动暂停推荐。" : "当前经营状态允许进入游客推荐候选池。"
                )
        );
    }

    private List<String> mergeSuggestions(
            MerchantResourceCreateRequest request,
            List<String> currentTags,
            List<String> generatedSuggestions,
            boolean soldOut
    ) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        if (!StringUtils.hasText(request.summary())) {
            suggestions.add("确认智能补全的游客侧简介，避免超出实际服务范围。");
        }
        if (currentTags.size() < 2) {
            suggestions.add("补充场景和服务特征标签，提高行程检索命中率。");
        }
        if (!StringUtils.hasText(request.openHours())) {
            suggestions.add("补充开放时间，便于 OpenClaw 安排行程时段。");
        }
        if (soldOut) {
            suggestions.add("更新可预约余量后再发布，避免游客获得无效推荐。");
        }
        suggestions.addAll(generatedSuggestions);
        return suggestions.stream().filter(StringUtils::hasText).limit(3).toList();
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private List<String> readTextArray(JsonNode node, int limit, int maxLength) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = cleanText(item.asText(), maxLength);
            if (StringUtils.hasText(value) && !values.contains(value)) {
                values.add(value);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private String cleanText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String categoryLabel(MerchantResourceCreateRequest request) {
        return switch (request.category()) {
            case SCENIC -> "自然景观";
            case CULTURE -> "文化场馆";
            case EXPERIENCE -> "非遗体验";
            case FOOD -> "本地餐饮";
            case LODGING -> "住宿";
            case LEISURE -> "城市休闲";
        };
    }

    private record DraftEnhancement(
            String summary,
            List<String> tags,
            List<String> suggestions
    ) {
    }
}
