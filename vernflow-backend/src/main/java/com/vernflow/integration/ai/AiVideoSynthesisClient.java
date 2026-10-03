package com.vernflow.integration.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vernflow.common.exception.BusinessException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/**
 * AI 成片客户端：调用 vernflow-ai 的视频合成接口
 * （真实图片 + 已核验事实 → DeepSeek 解说词 + edge-tts 配音 + 字幕运镜合成 mp4）。
 */
@Component
public class AiVideoSynthesisClient {

    private final RestClient restClient;

    public AiVideoSynthesisClient(@Qualifier("ragServiceRestClient") RestClient ragServiceRestClient) {
        this.restClient = ragServiceRestClient;
    }

    public byte[] synthesizeVideo(SynthesisRequest request) {
        try {
            byte[] response = restClient.post()
                    .uri("/ai/video-synthesis")
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            return Objects.requireNonNull(response, "AI video synthesis returned no content.");
        } catch (RestClientException exception) {
            throw new BusinessException("AI 成片服务调用失败：" + exception.getMessage());
        }
    }

    public record SynthesisRequest(
            String name,
            String city,
            String category,
            String facts,
            List<String> images,
            @JsonProperty("aspect_ratio") String aspectRatio
    ) {
    }
}
