package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 游客端景点导览视频生成请求。
 *
 * @param resourceId 景点资源 id
 * @param images     分镜图片（JPEG base64，前端已降采样，每张 ≤8MB）；AI 成片模式必填，可灵图生视频取第一张
 */
public record VideoGenerateRequest(
        @NotNull Long resourceId,
        @NotNull @Min(3) @Max(15) Integer durationSeconds,
        @NotBlank @Pattern(regexp = "16:9|9:16|1:1") String aspectRatio,
        @NotBlank @Pattern(regexp = "720p|1080p") String resolution,
        @Size(max = 300) String promptHint,
        @Size(max = 4) List<@Size(max = 8000000) String> images
) {
}
