package com.vernflow.integration.kling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.KlingProperties;
import com.vernflow.common.exception.BusinessException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;

/**
 * 可灵 Kling 视频生成 API 客户端（新版 API 标准，Bearer API Key 鉴权）。
 * 官方文档：https://kling.ai/document-api/llms.txt
 * 路径为 3.0 Turbo 模型；若官方路径调整，仅需修改下方常量或通过 KLING_BASE_URL 追加前缀。
 */
@Component
public class KlingClient {

    private static final String TEXT_TO_VIDEO_PATH = "/text-to-video/kling-3.0-turbo";
    private static final String IMAGE_TO_VIDEO_PATH = "/image-to-video/kling-3.0-turbo";
    private static final String TASKS_QUERY_PATH = "/tasks";

    private final RestClient restClient;
    private final KlingProperties properties;
    private final ObjectMapper objectMapper;

    public KlingClient(
            @Qualifier("klingRestClient") RestClient restClient,
            KlingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 是否已配置真实 API Key（false = Mock 模式，服务层据此跳过真实调用）。 */
    public boolean available() {
        return !properties.mockEnabled();
    }

    public CreateTaskResult createTextToVideo(
            String prompt,
            int durationSeconds,
            String aspectRatio,
            String resolution,
            String externalTaskId
    ) {
        TextToVideoRequest request = new TextToVideoRequest(
                prompt,
                new VideoSettings(aspectRatio, durationSeconds, resolution),
                new TaskOptions(null, externalTaskId, new WatermarkInfo(false))
        );
        CreateVideoResponse response = post(TEXT_TO_VIDEO_PATH, request);
        return requireCreated(response);
    }

    public CreateTaskResult createImageToVideo(
            String prompt,
            String imageBase64,
            int durationSeconds,
            String aspectRatio,
            String resolution,
            String externalTaskId
    ) {
        ImageToVideoRequest request = new ImageToVideoRequest(
                List.of(
                        new ContentItem("prompt", prompt, null),
                        new ContentItem("first_frame", null, imageBase64)
                ),
                new VideoSettings(aspectRatio, durationSeconds, resolution),
                new TaskOptions(null, externalTaskId, new WatermarkInfo(false))
        );
        CreateVideoResponse response = post(IMAGE_TO_VIDEO_PATH, request);
        return requireCreated(response);
    }

    /** 查询单个任务：返回状态与成功时第一条视频结果的 URL。 */
    public TaskQueryResult queryTask(String taskId) {
        try {
            TaskQueryResponse response = restClient.get()
                    .uri(TASKS_QUERY_PATH + "?task_ids={taskId}", taskId)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .retrieve()
                    .body(TaskQueryResponse.class);
            Objects.requireNonNull(response, "Kling task query returned no response.");
            if (response.code() != 0 || response.data() == null || response.data().isEmpty()) {
                throw new BusinessException("可灵任务查询失败：" + response.message());
            }
            TaskQueryData data = response.data().get(0);
            String videoUrl = data.outputs() == null ? null : data.outputs().stream()
                    .filter(item -> "video".equals(item.type()))
                    .map(OutputItem::url)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            return new TaskQueryResult(data.status(), videoUrl);
        } catch (RestClientResponseException exception) {
            throw friendlyKlingError(exception);
        } catch (RestClientException exception) {
            throw new BusinessException("可灵服务暂不可用：" + exception.getMessage());
        }
    }

    /** 下载视频字节（可灵视频 URL 30 天过期，需及时转存本地）。 */
    public byte[] downloadVideo(String videoUrl) {
        try {
            byte[] body = restClient.get()
                    .uri(videoUrl)
                    .header("User-Agent", "VernFlow/1.0")
                    .retrieve()
                    .body(byte[].class);
            return Objects.requireNonNull(body, "Kling video download returned no content.");
        } catch (RestClientException exception) {
            throw new BusinessException("视频下载失败：" + exception.getMessage());
        }
    }

    private CreateVideoResponse post(String path, Object requestBody) {
        try {
            CreateVideoResponse response = restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(CreateVideoResponse.class);
            return Objects.requireNonNull(response, "Kling create task returned no response.");
        } catch (RestClientResponseException exception) {
            throw friendlyKlingError(exception);
        } catch (RestClientException exception) {
            throw new BusinessException("可灵服务暂不可用：" + exception.getMessage());
        }
    }

    /**
     * 把可灵返回的错误码翻译成可读的中文提示（错误码表见官方文档 error-codes）。
     */
    private BusinessException friendlyKlingError(RestClientResponseException exception) {
        String serverMessage = null;
        Integer serviceCode = null;
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            serviceCode = body.hasNonNull("code") ? body.get("code").asInt() : null;
            serverMessage = body.path("message").asText(null);
        } catch (Exception ignored) {
            // 响应体不是标准 JSON 时走通用文案。
        }
        String hint = switch (serviceCode == null ? -1 : serviceCode) {
            case 1000, 1001, 1002, 1003, 1004 -> "可灵接口鉴权失败，请检查 KLING_API_KEY 配置是否正确。";
            case 1100 -> "可灵账户状态异常，请联系可灵客服确认。";
            case 1101 -> "可灵账户已欠费，请充值后重试。";
            case 1102 -> "可灵账户资源包已用完或过期，请到 kling.ai 控制台购买资源包或开通后付费。";
            case 1103 -> "当前账户无权访问该模型，请确认可灵控制台的模型权限。";
            case 1200, 1201 -> "可灵请求参数无效：" + fallback(serverMessage, "请检查生成参数后重试。");
            case 1301 -> "内容未通过可灵内容安全审核，请调整提示词后重试。";
            case 1302, 1303 -> "可灵请求过于频繁，请稍后重试。";
            case 5000, 5001, 5002 -> "可灵服务暂时不可用，请稍后重试。";
            default -> "可灵请求失败(HTTP " + exception.getStatusCode().value() + ")："
                    + fallback(serverMessage, exception.getMessage());
        };
        return new BusinessException(hint);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private CreateTaskResult requireCreated(CreateVideoResponse response) {
        if (response.code() != 0 || response.data() == null || response.data().id() == null) {
            throw new BusinessException("可灵视频创建失败：" + response.message());
        }
        return new CreateTaskResult(response.data().id(), response.data().status());
    }

    public record CreateTaskResult(String taskId, String status) {
    }

    public record TaskQueryResult(String status, String videoUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ContentItem(String type, String text, String url) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record VideoSettings(
            @JsonProperty("aspect_ratio") String aspectRatio,
            int duration,
            String resolution
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TaskOptions(
            @JsonProperty("callback_url") String callbackUrl,
            @JsonProperty("external_task_id") String externalTaskId,
            @JsonProperty("watermark_info") WatermarkInfo watermarkInfo
    ) {
    }

    private record WatermarkInfo(boolean enabled) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TextToVideoRequest(
            String prompt,
            VideoSettings settings,
            TaskOptions options
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ImageToVideoRequest(
            List<ContentItem> contents,
            VideoSettings settings,
            TaskOptions options
    ) {
    }

    private record CreateVideoResponse(int code, String message, CreateVideoData data) {
    }

    private record CreateVideoData(String id, String status) {
    }

    private record TaskQueryResponse(int code, String message, List<TaskQueryData> data) {
    }

    private record TaskQueryData(String id, String status, List<OutputItem> outputs) {
    }

    private record OutputItem(String type, String url) {
    }
}
