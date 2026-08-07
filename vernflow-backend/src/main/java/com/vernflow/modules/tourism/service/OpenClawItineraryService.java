package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.TripRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OpenClawItineraryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClawItineraryService.class);
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
    private static final String INSTRUCTIONS = """
            你是 VernTrip 的结构化行程编排 Agent。
            必须先调用 verntrip_tourism_catalog_read 读取目的地资源，只能使用工具返回的资源名称。
            根据游客的日期、天数、同行人群、预算、节奏和兴趣，安排一份可执行的一至两日行程。
            每天安排 3 至 4 个节点；两日行程至少包含一项住宿；避免同一资源重复出现。
            cost 表示本次同行人群在该节点的预计总费用，所有节点费用之和不得超过总预算的 85%。
            最终只输出一个 JSON 对象，不要输出 Markdown、代码围栏、解释或额外文字。
            JSON 格式必须严格为：
            {
              "title":"方案标题",
              "summary":"不超过120字的方案摘要",
              "matchScore":88,
              "days":[
                {
                  "day":1,
                  "items":[
                    {
                      "time":"09:30",
                      "label":"简短时段标题",
                      "resourceName":"工具返回的完整资源名称",
                      "description":"结合游客条件说明安排理由",
                      "cost":120
                    }
                  ]
                }
              ]
            }
            """;

    private final OpenClawProperties openClawProperties;
    private final OpenClawSessionContextService sessionContextService;
    private final OpenClawGatewayClient gatewayClient;
    private final ObjectMapper objectMapper;

    public OpenClawItineraryService(
            OpenClawProperties openClawProperties,
            OpenClawSessionContextService sessionContextService,
            OpenClawGatewayClient gatewayClient,
            ObjectMapper objectMapper
    ) {
        this.openClawProperties = openClawProperties;
        this.sessionContextService = sessionContextService;
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
    }

    public Optional<AgentItinerary> generate(
            Long userId,
            TripRequest request,
            List<TourismResource> availableResources
    ) {
        if (!openClawProperties.primaryEnabled() || availableResources.isEmpty()) {
            return Optional.empty();
        }

        try {
            OpenClawSessionContextService.BoundSession session = sessionContextService.bind(
                    userId,
                    null,
                    false,
                    List.of()
            );
            String prompt = buildPrompt(request);
            for (int attempt = 1; attempt <= 2; attempt++) {
                OpenClawGatewayClient.ShadowResponse response = gatewayClient.createResponse(
                        session.sessionKey(),
                        prompt,
                        INSTRUCTIONS
                );
                try {
                    return Optional.of(parseAndValidate(response.text(), request, availableResources));
                } catch (InvalidAgentPlanException exception) {
                    if (attempt == 2) {
                        throw exception;
                    }
                    prompt = buildRepairPrompt(exception.getMessage(), response.text());
                }
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenClaw itinerary generation fell back to local rules: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String buildRepairPrompt(String validationCode, String previousResponse) {
        return """
                Your previous itinerary JSON was rejected by VernTrip with validation code: %s.
                Regenerate the complete JSON object from scratch and output JSON only.
                Hard constraints:
                - each day must contain 3 or 4 items, never 5;
                - a two-day trip must contain exactly one lodging item and it must be on day 1;
                - resourceName must exactly match the catalog and cannot be repeated;
                - total cost must stay within 85%% of the user's budget.
                Previous candidate:
                %s
                """.formatted(validationCode, previousResponse);
    }

    private String buildPrompt(TripRequest request) {
        return """
                请为以下游客生成结构化行程：
                目的地：%s
                出发地：%s
                出行日期：%s
                天数：%d
                同行人群：%s
                总预算：%s 元
                行程节奏：%s
                兴趣：%s
                补充要求：%s
                请先读取目的地资源目录，再输出严格 JSON。
                """.formatted(
                request.getDestination(),
                textOrNone(request.getDepartureCity()),
                request.getTravelDate(),
                request.getDays(),
                request.getTravelerType(),
                request.getBudget().stripTrailingZeros().toPlainString(),
                request.getPace(),
                request.getInterestsJson(),
                textOrNone(request.getNotes())
        );
    }

    private AgentItinerary parseAndValidate(
            String responseText,
            TripRequest request,
            List<TourismResource> availableResources
    ) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(responseText));
            String title = requiredText(root, "title", 180);
            String summary = requiredText(root, "summary", 1200);
            int matchScore = root.path("matchScore").asInt(-1);
            if (matchScore < 60 || matchScore > 100) {
                throw new InvalidAgentPlanException("match_score_invalid");
            }

            JsonNode days = root.path("days");
            if (!days.isArray() || days.size() != request.getDays()) {
                throw new InvalidAgentPlanException("day_count_invalid");
            }

            Map<String, TourismResource> resourcesByName = new HashMap<>();
            availableResources.forEach(resource -> resourcesByName.put(resource.getName(), resource));
            List<AgentStop> stops = new ArrayList<>();
            Set<String> usedResources = new HashSet<>();
            int lodgingDay = 0;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
                JsonNode day = days.get(dayIndex);
                int dayNumber = day.path("day").asInt(-1);
                JsonNode items = day.path("items");
                if (dayNumber != dayIndex + 1 || !items.isArray() || items.size() < 3 || items.size() > 4) {
                    throw new InvalidAgentPlanException("day_items_invalid");
                }

                for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                    JsonNode item = items.get(itemIndex);
                    String time = requiredText(item, "time", 8);
                    if (!TIME_PATTERN.matcher(time).matches()) {
                        throw new InvalidAgentPlanException("time_invalid");
                    }
                    String resourceName = requiredText(item, "resourceName", 160);
                    TourismResource resource = resourcesByName.get(resourceName);
                    if (resource == null || !usedResources.add(resourceName)) {
                        throw new InvalidAgentPlanException("resource_invalid_or_repeated");
                    }

                    BigDecimal cost = item.path("cost").decimalValue();
                    if (!item.path("cost").isNumber() || cost.signum() < 0) {
                        throw new InvalidAgentPlanException("cost_invalid");
                    }
                    totalCost = totalCost.add(cost);
                    if (resource.getCategory() == TourismResource.Category.LODGING) {
                        if (lodgingDay != 0) {
                            throw new InvalidAgentPlanException("lodging_count_invalid");
                        }
                        lodgingDay = dayNumber;
                    }
                    stops.add(new AgentStop(
                            dayNumber,
                            itemIndex + 1,
                            time,
                            requiredText(item, "label", 80),
                            resourceName,
                            requiredText(item, "description", 1200),
                            categoryTag(resource.getCategory()),
                            cost.setScale(0, java.math.RoundingMode.HALF_UP)
                    ));
                }
            }

            BigDecimal costLimit = request.getBudget().multiply(BigDecimal.valueOf(0.85));
            if (totalCost.compareTo(costLimit) > 0
                    || (request.getDays() > 1 && lodgingDay != 1)) {
                throw new InvalidAgentPlanException("budget_or_lodging_invalid");
            }
            return new AgentItinerary(title, summary, matchScore, List.copyOf(stops));
        } catch (InvalidAgentPlanException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAgentPlanException("response_json_invalid", exception);
        }
    }

    private String extractJson(String responseText) {
        int start = responseText == null ? -1 : responseText.indexOf('{');
        int end = responseText == null ? -1 : responseText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidAgentPlanException("response_json_missing");
        }
        return responseText.substring(start, end + 1);
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : null;
        if (!StringUtils.hasText(value) || value.length() > maxLength) {
            throw new InvalidAgentPlanException(field + "_invalid");
        }
        return value;
    }

    private String categoryTag(TourismResource.Category category) {
        return switch (category) {
            case SCENIC -> "自然风景";
            case CULTURE -> "文化体验";
            case EXPERIENCE -> "非遗体验";
            case FOOD -> "本地美食";
            case LODGING -> "住宿";
            case LEISURE -> "人文历史";
        };
    }

    private String textOrNone(String value) {
        return StringUtils.hasText(value) ? value : "无";
    }

    public record AgentItinerary(
            String title,
            String summary,
            int matchScore,
            List<AgentStop> stops
    ) {
    }

    public record AgentStop(
            int day,
            int sequence,
            String time,
            String label,
            String resourceName,
            String description,
            String tag,
            BigDecimal cost
    ) {
    }

    private static class InvalidAgentPlanException extends RuntimeException {

        InvalidAgentPlanException(String message) {
            super(message);
        }

        InvalidAgentPlanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
