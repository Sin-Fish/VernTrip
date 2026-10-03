package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.kling.KlingClient;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoTaskDispatcherTest {

    @TempDir
    Path mediaDir;

    private final VideoGenerationTaskRepository taskRepository = mock(VideoGenerationTaskRepository.class);
    private final KlingClient klingClient = mock(KlingClient.class);

    private VideoTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new VideoTaskDispatcher(
                taskRepository, klingClient, new StorageProperties(null, null, mediaDir)
        );
    }

    @Test
    void stalePendingTaskFailsWithTimeoutMessage() {
        VideoGenerationTask task = pendingTask(1L, VideoGenerationTask.Provider.LOCAL, LocalDateTime.now().minusMinutes(12));
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PENDING))
                .thenReturn(List.of(task));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(task.getErrorMessage()).contains("排队超时");
    }

    @Test
    void freshPendingTaskIsLeftAlone() {
        VideoGenerationTask task = pendingTask(1L, VideoGenerationTask.Provider.LOCAL, LocalDateTime.now());
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PENDING))
                .thenReturn(List.of(task));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.PENDING);
    }

    @Test
    void localProcessingTaskIsNotTouchedByKlingPolling() {
        VideoGenerationTask task = processingTask(1L, null, LocalDateTime.now(), VideoGenerationTask.Provider.LOCAL);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.PROCESSING);
        verify(klingClient, never()).queryTask(any());
    }

    @Test
    void realTaskSucceededDownloadsAndStoresVideo() throws Exception {
        VideoGenerationTask task = processingTask(1L, "kling-1", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));
        when(klingClient.queryTask("kling-1"))
                .thenReturn(new KlingClient.TaskQueryResult("succeeded", "https://kling.example/video.mp4"));
        when(klingClient.downloadVideo("https://kling.example/video.mp4")).thenReturn(new byte[]{1, 2, 3});

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.SUCCEEDED);
        assertThat(task.getKlingVideoUrl()).isEqualTo("https://kling.example/video.mp4");
        assertThat(Files.readAllBytes(mediaDir.resolve("video-1.mp4"))).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void realTaskKlingFailureMarksFailed() {
        VideoGenerationTask task = processingTask(1L, "kling-1", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));
        when(klingClient.queryTask("kling-1"))
                .thenReturn(new KlingClient.TaskQueryResult("failed", null));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
    }

    @Test
    void queryExceptionIncrementsAttemptsAndKeepsProcessing() {
        VideoGenerationTask task = processingTask(1L, "kling-1", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));
        when(klingClient.queryTask("kling-1"))
                .thenThrow(new BusinessException("可灵服务暂不可用：网络错误"));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.PROCESSING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void downloadFailureFailsAfterThreeAttempts() {
        VideoGenerationTask task = processingTask(1L, "kling-1", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        ReflectionTestUtils.setField(task, "attemptCount", 2);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));
        when(klingClient.queryTask("kling-1"))
                .thenReturn(new KlingClient.TaskQueryResult("succeeded", "https://kling.example/video.mp4"));
        when(klingClient.downloadVideo("https://kling.example/video.mp4"))
                .thenThrow(new BusinessException("视频下载失败：连接超时"));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(task.getErrorMessage()).contains("视频下载失败");
    }

    @Test
    void staleTaskFailsWithTimeoutMessage() {
        VideoGenerationTask task = processingTask(1L, "kling-1", LocalDateTime.now().minusMinutes(20), VideoGenerationTask.Provider.KLING);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(task));

        dispatcher.dispatchDueTasks();

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(task.getErrorMessage()).contains("超时");
        verify(klingClient, never()).queryTask(any());
    }

    @Test
    void atMostThreeRealTasksAdvancePerTick() {
        VideoGenerationTask first = processingTask(1L, "kling-1", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        VideoGenerationTask second = processingTask(2L, "kling-2", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        VideoGenerationTask third = processingTask(3L, "kling-3", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        VideoGenerationTask fourth = processingTask(4L, "kling-4", LocalDateTime.now(), VideoGenerationTask.Provider.KLING);
        when(taskRepository.findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status.PROCESSING))
                .thenReturn(List.of(first, second, third, fourth));
        when(klingClient.queryTask(any()))
                .thenReturn(new KlingClient.TaskQueryResult("failed", null));

        dispatcher.dispatchDueTasks();

        assertThat(first.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(second.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(third.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(fourth.getStatus()).isEqualTo(VideoGenerationTask.Status.PROCESSING);
    }

    private VideoGenerationTask pendingTask(Long id, VideoGenerationTask.Provider provider, LocalDateTime createdAt) {
        VideoGenerationTask task = VideoGenerationTask.pending(
                null, null, 7L, VideoGenerationTask.TaskType.TEXT2VIDEO,
                provider, "p", 5, "16:9", "720p", false
        );
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", createdAt);
        ReflectionTestUtils.setField(task, "updatedAt", createdAt);
        return task;
    }

    private VideoGenerationTask processingTask(
            Long id,
            String klingTaskId,
            LocalDateTime updatedAt,
            VideoGenerationTask.Provider provider
    ) {
        VideoGenerationTask task = VideoGenerationTask.pending(
                null, null, 7L, VideoGenerationTask.TaskType.TEXT2VIDEO,
                provider, "p", 5, "16:9", "720p", false
        );
        ReflectionTestUtils.setField(task, "id", id);
        if (klingTaskId == null) {
            task.markProcessing();
        } else {
            task.markProcessing(klingTaskId);
        }
        ReflectionTestUtils.setField(task, "updatedAt", updatedAt);
        return task;
    }
}
