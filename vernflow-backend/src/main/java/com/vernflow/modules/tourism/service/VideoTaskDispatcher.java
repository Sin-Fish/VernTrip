package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.kling.KlingClient;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频任务派发器：推进可灵真实任务（查询/下载转存），并清理超时的孤儿任务。
 * AI 成片（LOCAL）任务由 {@link VideoSynthesisWorker} 异步执行，不经过本派发器。
 */
@Service
public class VideoTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(VideoTaskDispatcher.class);

    /** 每 tick 最多推进的真实任务数，保护可灵额度。 */
    private static final int MAX_REAL_PROCESSING = 3;
    private static final Duration REAL_TASK_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final int MAX_QUERY_ATTEMPTS = 10;

    private final VideoGenerationTaskRepository taskRepository;
    private final KlingClient klingClient;
    private final StorageProperties storageProperties;
    private final AtomicBoolean dispatching = new AtomicBoolean(false);

    public VideoTaskDispatcher(
            VideoGenerationTaskRepository taskRepository,
            KlingClient klingClient,
            StorageProperties storageProperties
    ) {
        this.taskRepository = taskRepository;
        this.klingClient = klingClient;
        this.storageProperties = storageProperties;
    }

    @Scheduled(fixedDelay = 3000L)
    public void scheduledDispatch() {
        dispatchDueTasks();
    }

    public void dispatchDueTasks() {
        if (!dispatching.compareAndSet(false, true)) {
            return;
        }
        try {
            expireStalePendingTasks();
            advanceRealTasks();
        } finally {
            dispatching.set(false);
        }
    }

    /** PENDING 超过 10 分钟仍未开始执行（服务重启丢任务等）→ 标记失败。 */
    private void expireStalePendingTasks() {
        LocalDateTime dueBefore = LocalDateTime.now().minus(PENDING_TIMEOUT);
        for (VideoGenerationTask task : taskRepository
                .findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PENDING)) {
            if (!task.getCreatedAt().isBefore(dueBefore)) {
                continue;
            }
            try {
                task.markFailed("任务排队超时，请重新发起生成。");
                taskRepository.save(task);
            } catch (Exception exception) {
                log.warn("Failed to expire stale video task {}.", task.getId(), exception);
            }
        }
    }

    /** 可灵真实任务：轮询结果，成功后下载视频转存本地。 */
    private void advanceRealTasks() {
        List<VideoGenerationTask> processing = taskRepository
                .findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING);
        int advanced = 0;
        for (VideoGenerationTask task : processing) {
            if (task.getProvider() != VideoGenerationTask.Provider.KLING
                    || !StringUtils.hasText(task.getKlingTaskId())) {
                continue;
            }
            if (advanced >= MAX_REAL_PROCESSING) {
                break;
            }
            advanced += 1;
            try {
                advanceRealTask(task);
                taskRepository.save(task);
            } catch (Exception exception) {
                log.warn("Failed to advance real video task {}.", task.getId(), exception);
                task.markFailed("视频任务处理异常：" + exception.getMessage());
                taskRepository.save(task);
            }
        }
    }

    private void advanceRealTask(VideoGenerationTask task) {
        if (task.getUpdatedAt().isBefore(LocalDateTime.now().minus(REAL_TASK_TIMEOUT))) {
            task.markFailed("视频生成超时，请重试。");
            return;
        }
        KlingClient.TaskQueryResult result;
        try {
            result = klingClient.queryTask(task.getKlingTaskId());
        } catch (BusinessException exception) {
            failAfterAttempts(task, MAX_QUERY_ATTEMPTS, exception.getMessage());
            return;
        }
        switch (result.status()) {
            case "succeeded" -> {
                if (!StringUtils.hasText(result.videoUrl())) {
                    failAfterAttempts(task, MAX_DOWNLOAD_ATTEMPTS, "可灵返回成功但未包含视频地址。");
                    return;
                }
                try {
                    byte[] video = klingClient.downloadVideo(result.videoUrl());
                    Path target = mediaPath(task);
                    Files.createDirectories(target.getParent());
                    Files.write(target, video);
                    task.markSucceeded(target.toAbsolutePath().toString(), result.videoUrl());
                } catch (Exception exception) {
                    failAfterAttempts(task, MAX_DOWNLOAD_ATTEMPTS, "视频下载失败：" + exception.getMessage());
                }
            }
            case "failed" -> task.markFailed("可灵生成失败，请调整提示词后重试。");
            default -> task.touch(); // submitted / processing：保持并刷新存活时间
        }
    }

    private void failAfterAttempts(VideoGenerationTask task, int maxAttempts, String errorMessage) {
        if (task.recordAttempt() >= maxAttempts) {
            task.markFailed(errorMessage);
        }
    }

    private Path mediaPath(VideoGenerationTask task) {
        return storageProperties.mediaDir().resolve("video-" + task.getId() + ".mp4");
    }
}
