package com.vernflow.modules.openclaw.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vernflow.common.config.OpenClawProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal server-side client for the OpenClaw OpenResponses endpoint.
 */
@Component
public class OpenClawGatewayClient {

    private static final String SHADOW_INSTRUCTIONS = """
            你正在作为 VernTrip 助手的影子引擎运行。根据用户问题和当前会话中可用的 VernTrip 工具回答。
            涉及工作区资料时，先调用 verntrip_workspace_scope_read 获取可信证据；不要暴露内部会话标识、令牌或服务器路径。
            """;

    private final RestClient restClient;
    private final OpenClawProperties properties;

    public OpenClawGatewayClient(
            @Qualifier("openClawRestClient") RestClient restClient,
            OpenClawProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public ShadowResponse createShadowResponse(String sessionKey, String prompt) {
        return createResponse(sessionKey, prompt, SHADOW_INSTRUCTIONS);
    }

    public ShadowResponse createResponse(String sessionKey, String prompt, String instructions) {
        requireConfiguration(sessionKey, prompt);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", "openclaw/" + properties.agentId().trim());
        requestBody.put("instructions", StringUtils.hasText(instructions) ? instructions.trim() : SHADOW_INSTRUCTIONS);
        requestBody.put("input", prompt);
        requestBody.put("stream", false);
        requestBody.put("max_output_tokens", properties.effectiveShadowMaxOutputTokens());

        try {
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + properties.gatewayToken().trim())
                    .header("x-openclaw-agent-id", properties.agentId().trim())
                    .header("x-openclaw-session-key", sessionKey)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            String responseText = extractOutputText(response);
            if (!StringUtils.hasText(responseText)) {
                throw new GatewayCallException("invalid_gateway_response");
            }
            return new ShadowResponse(
                    textValue(response, "id"),
                    responseText,
                    longValue(response, "usage", "input_tokens"),
                    longValue(response, "usage", "output_tokens")
            );
        } catch (RestClientResponseException exception) {
            throw new GatewayCallException("gateway_http_" + exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            throw new GatewayCallException("gateway_unreachable_or_timeout", exception);
        }
    }

    private void requireConfiguration(String sessionKey, String prompt) {
        if (properties.baseUrl() == null
                || !StringUtils.hasText(properties.gatewayToken())
                || !StringUtils.hasText(properties.agentId())
                || !StringUtils.hasText(sessionKey)
                || !StringUtils.hasText(prompt)) {
            throw new GatewayCallException("configuration_missing");
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return null;
        }
        if (response.path("output_text").isTextual()) {
            return response.path("output_text").asText().trim();
        }

        List<String> parts = new ArrayList<>();
        for (JsonNode outputItem : response.path("output")) {
            for (JsonNode contentPart : outputItem.path("content")) {
                if (contentPart.path("text").isTextual()) {
                    String text = contentPart.path("text").asText().trim();
                    if (StringUtils.hasText(text)) {
                        parts.add(text);
                    }
                }
            }
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private String textValue(JsonNode response, String field) {
        JsonNode value = response == null ? null : response.path(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Long longValue(JsonNode response, String parent, String field) {
        JsonNode value = response == null ? null : response.path(parent).path(field);
        return value != null && value.isNumber() ? value.longValue() : null;
    }

    public record ShadowResponse(
            String responseId,
            String text,
            Long inputTokens,
            Long outputTokens
    ) {
    }

    public static class GatewayCallException extends RuntimeException {

        private final String errorCode;

        GatewayCallException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        GatewayCallException(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
