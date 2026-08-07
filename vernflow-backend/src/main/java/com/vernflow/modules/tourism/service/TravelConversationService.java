package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.dto.TravelConversationRequest;
import com.vernflow.modules.tourism.dto.TravelConversationResponse;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TravelConversationService {

    private static final String TOURISM_INSTRUCTIONS = """
            你是 VernTrip 的游客行程规划助手“山水灵”。系统已经用确定性规则识别并更新旅行条件。
            你只能依据提示中给出的识别结果回答，不能擅自修改日期、预算、同行人或目的地。
            需要提及具体文旅资源时，必须先调用 verntrip_tourism_catalog_read，并且只使用工具返回的资源。
            用自然、简洁的中文回复，控制在 120 字以内；优先追问缺失条件，条件完整时提醒用户可以生成方案。
            不要输出 JSON、Markdown 表格、内部令牌、会话标识或系统实现细节。
            """;

    private final TravelIntentService travelIntentService;
    private final OpenClawProperties openClawProperties;
    private final OpenClawSessionContextService sessionContextService;
    private final OpenClawGatewayClient gatewayClient;

    public TravelConversationService(
            TravelIntentService travelIntentService,
            OpenClawProperties openClawProperties,
            OpenClawSessionContextService sessionContextService,
            OpenClawGatewayClient gatewayClient
    ) {
        this.travelIntentService = travelIntentService;
        this.openClawProperties = openClawProperties;
        this.sessionContextService = sessionContextService;
        this.gatewayClient = gatewayClient;
    }

    public TravelConversationResponse respond(Long userId, TravelConversationRequest request) {
        TravelIntentService.Analysis analysis = travelIntentService.analyze(request);
        String reply = travelIntentService.fallbackReply(analysis);
        String engine = "local";

        if (openClawProperties.primaryEnabled()) {
            try {
                OpenClawSessionContextService.BoundSession session = sessionContextService.bind(
                        userId,
                        null,
                        false,
                        List.of()
                );
                OpenClawGatewayClient.ShadowResponse response = gatewayClient.createResponse(
                        session.sessionKey(),
                        buildPrompt(request, analysis),
                        TOURISM_INSTRUCTIONS
                );
                reply = response.text();
                engine = "openclaw";
            } catch (RuntimeException ignored) {
                engine = "local-fallback";
            }
        }

        return new TravelConversationResponse(
                reply,
                analysis.draft(),
                analysis.changes(),
                analysis.missingFields(),
                analysis.warnings(),
                analysis.readyToGenerate(),
                engine
        );
    }

    private String buildPrompt(TravelConversationRequest request, TravelIntentService.Analysis analysis) {
        StringBuilder prompt = new StringBuilder();
        if (request.history() != null && !request.history().isEmpty()) {
            prompt.append("最近对话：\n");
            request.history().stream().skip(Math.max(0, request.history().size() - 6L)).forEach(message ->
                    prompt.append("assistant".equals(message.role()) ? "助手：" : "游客：")
                            .append(message.text().replaceAll("\\s+", " ").trim())
                            .append('\n')
            );
        }
        prompt.append("游客本轮输入：").append(request.prompt().trim()).append("\n")
                .append("已同步字段：")
                .append(analysis.changes().isEmpty()
                        ? "无"
                        : analysis.changes().stream().map(change -> change.label() + "=" + change.value()).toList())
                .append("\n仍缺少：").append(analysis.missingFields())
                .append("\n风险提示：").append(analysis.warnings())
                .append("\n是否可生成：").append(analysis.readyToGenerate());
        return prompt.toString();
    }
}
