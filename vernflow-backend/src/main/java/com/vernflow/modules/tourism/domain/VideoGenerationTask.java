package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * AI 视频生成任务（可灵 Kling）。
 * 创建时同步向可灵提交任务并进入 PROCESSING，由 {@code VideoTaskDispatcher} 轮询结果；
 * 未配置 KLING_API_KEY 时任务为 mock 模式，模拟延迟后直接置为 SUCCEEDED 并指向内置样例视频。
 */
@Entity
@Table(name = "video_generation_task")
public class VideoGenerationTask {

    public enum TaskType {
        TEXT2VIDEO,
        IMAGE2VIDEO
    }

    public enum Status {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }

    public enum Provider {
        KLING,
        LOCAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 24)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Provider provider;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "aspect_ratio", nullable = false, length = 16)
    private String aspectRatio;

    @Column(nullable = false, length = 16)
    private String resolution;

    @Column(name = "kling_task_id", length = 80)
    private String klingTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "video_path", columnDefinition = "TEXT")
    private String videoPath;

    @Column(name = "kling_video_url", columnDefinition = "TEXT")
    private String klingVideoUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean mock;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected VideoGenerationTask() {
    }

    private VideoGenerationTask(
            Long resourceId,
            Long productId,
            Long createdBy,
            TaskType taskType,
            Provider provider,
            String prompt,
            int durationSeconds,
            String aspectRatio,
            String resolution,
            boolean mock
    ) {
        this.resourceId = resourceId;
        this.productId = productId;
        this.createdBy = createdBy;
        this.taskType = taskType;
        this.provider = provider;
        this.prompt = prompt;
        this.durationSeconds = durationSeconds;
        this.aspectRatio = aspectRatio;
        this.resolution = resolution;
        this.mock = mock;
        this.status = Status.PENDING;
        this.attemptCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static VideoGenerationTask pending(
            Long resourceId,
            Long productId,
            Long createdBy,
            TaskType taskType,
            Provider provider,
            String prompt,
            int durationSeconds,
            String aspectRatio,
            String resolution,
            boolean mock
    ) {
        return new VideoGenerationTask(
                resourceId,
                productId,
                createdBy,
                taskType,
                provider,
                prompt,
                durationSeconds,
                aspectRatio,
                resolution,
                mock
        );
    }

    public void markProcessing(String klingTaskId) {
        this.klingTaskId = klingTaskId;
        this.status = Status.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** AI 成片（LOCAL）任务开始执行。 */
    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(String videoPath, String klingVideoUrl) {
        this.videoPath = videoPath;
        this.klingVideoUrl = klingVideoUrl;
        this.status = Status.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = Status.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** 保持当前状态，仅刷新 updatedAt（轮询存活信号，超时判据依赖它）。 */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 查询/下载失败次数 +1，返回累计次数供派发器判断是否放弃。 */
    public int recordAttempt() {
        this.attemptCount += 1;
        this.updatedAt = LocalDateTime.now();
        return this.attemptCount;
    }

    public Long getId() {
        return id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public String getResolution() {
        return resolution;
    }

    public String getKlingTaskId() {
        return klingTaskId;
    }

    public Status getStatus() {
        return status;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public String getKlingVideoUrl() {
        return klingVideoUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isMock() {
        return mock;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
