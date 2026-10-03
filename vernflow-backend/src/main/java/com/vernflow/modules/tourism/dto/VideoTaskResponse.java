package com.vernflow.modules.tourism.dto;

import java.time.LocalDateTime;

/**
 * 视频生成任务状态。SUCCEEDED 时 videoUrl 为本地播放地址（/api/tourism/videos/{id}/file）。
 *
 * @param provider 生成方式：KLING = 可灵真实 AI 视频；LOCAL = AI 成片
 */
public record VideoTaskResponse(
        Long id,
        String status,
        String taskType,
        String provider,
        boolean mock,
        Integer durationSeconds,
        String aspectRatio,
        String resolution,
        String videoUrl,
        String errorMessage,
        String klingVideoUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
