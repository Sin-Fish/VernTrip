package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.integration.ai.AiVideoSynthesisClient;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoSynthesisWorkerTest {

    @TempDir
    Path mediaDir;

    private final VideoGenerationTaskRepository taskRepository = mock(VideoGenerationTaskRepository.class);
    private final TourismResourceRepository resourceRepository = mock(TourismResourceRepository.class);
    private final MerchantProductService productService = mock(MerchantProductService.class);
    private final VideoPromptBuilder promptBuilder = mock(VideoPromptBuilder.class);
    private final AiVideoSynthesisClient synthesisClient = mock(AiVideoSynthesisClient.class);
    private final AttractionPhotoService attractionPhotoService = mock(AttractionPhotoService.class);

    private VideoSynthesisWorker worker;

    @BeforeEach
    void setUp() {
        worker = new VideoSynthesisWorker(
                taskRepository, resourceRepository, productService, promptBuilder, synthesisClient,
                attractionPhotoService, new StorageProperties(null, null, mediaDir)
        );
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attractionPhotoService.resolvePhotoBase64(any())).thenReturn(List.of());
    }

    @Test
    void successfulSynthesisStoresVideoAndMarksSucceeded() throws Exception {
        VideoGenerationTask task = pendingLocalTask(5L);
        TourismResource resource = publishedResource();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(promptBuilder.buildFactsText(resource)).thenReturn("开放时间08:00-17:00");
        when(synthesisClient.synthesizeVideo(any()))
                .thenReturn(new byte[]{9, 8, 7});

        worker.processTask(5L, List.of("aW1nMQ=="));

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.SUCCEEDED);
        assertThat(Files.readAllBytes(mediaDir.resolve("video-5.mp4"))).containsExactly((byte) 9, (byte) 8, (byte) 7);
        verify(synthesisClient).synthesizeVideo(argThat(request ->
                request.name().equals("灵隐寺")
                        && request.city().equals("杭州")
                        && request.category().equals("CULTURE")
                        && request.facts().contains("开放时间08:00-17:00")
                        && request.images().equals(List.of("aW1nMQ=="))
                        && request.aspectRatio().equals("16:9")
        ));
    }

    @Test
    void prefersAmapPhotosOverFallbackImages() {
        VideoGenerationTask task = pendingLocalTask(5L);
        TourismResource resource = publishedResource();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(attractionPhotoService.resolvePhotoBase64(resource)).thenReturn(List.of("cmVhbHBob3Rv"));
        when(synthesisClient.synthesizeVideo(any())).thenReturn(new byte[]{1});

        worker.processTask(5L, List.of("ZmFsbGJhY2s="));

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.SUCCEEDED);
        verify(synthesisClient).synthesizeVideo(argThat(request ->
                request.images().equals(List.of("cmVhbHBob3Rv"))
        ));
    }

    @Test
    void synthesisFailureMarksFailedWithMessage() {
        VideoGenerationTask task = pendingLocalTask(5L);
        TourismResource resource = publishedResource();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(synthesisClient.synthesizeVideo(any()))
                .thenThrow(new RuntimeException("AI 成片服务调用失败：连接被拒绝"));

        worker.processTask(5L, List.of("aW1nMQ=="));

        assertThat(task.getStatus()).isEqualTo(VideoGenerationTask.Status.FAILED);
        assertThat(task.getErrorMessage()).contains("AI 成片生成失败");
        assertThat(Files.exists(mediaDir.resolve("video-5.mp4"))).isFalse();
    }

    @Test
    void nonPendingTaskIsIgnored() {
        VideoGenerationTask task = pendingLocalTask(5L);
        task.markSucceeded("already.mp4", null);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        worker.processTask(5L, List.of("aW1nMQ=="));

        verify(synthesisClient, never()).synthesizeVideo(any());
    }

    private VideoGenerationTask pendingLocalTask(Long id) {
        VideoGenerationTask task = VideoGenerationTask.pending(
                1L, null, 7L, VideoGenerationTask.TaskType.IMAGE2VIDEO,
                VideoGenerationTask.Provider.LOCAL, "p", 5, "16:9", "720p", false
        );
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private TourismResource publishedResource() {
        TourismPlace place = TourismPlace.verified(
                "杭州", "灵隐寺", "西湖区", "法云弄1号", "POI-1",
                BigDecimal.valueOf(120.1), BigDecimal.valueOf(30.2), "高德"
        );
        TourismResource resource = TourismResource.published(
                place, "灵隐寺", TourismResource.Category.CULTURE, "千年古刹。",
                "[\"禅意\"]", "[]", "高德", "https://example.com",
                "08:00-17:00", BigDecimal.valueOf(30), BigDecimal.valueOf(45)
        );
        ReflectionTestUtils.setField(resource, "id", 1L);
        return resource;
    }
}
