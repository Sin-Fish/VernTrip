package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.KlingProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.kling.KlingClient;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.dto.MerchantVideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoTaskResponse;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoGenerationServiceTest {

    private final TourismResourceRepository resourceRepository = mock(TourismResourceRepository.class);
    private final VideoGenerationTaskRepository taskRepository = mock(VideoGenerationTaskRepository.class);
    private final VideoPromptBuilder promptBuilder = mock(VideoPromptBuilder.class);
    private final KlingClient klingClient = mock(KlingClient.class);
    private final MerchantProductService productService = mock(MerchantProductService.class);
    private final VideoSynthesisWorker synthesisWorker = mock(VideoSynthesisWorker.class);

    private KlingProperties mockProperties;
    private VideoGenerationService service;

    @BeforeEach
    void setUp() {
        mockProperties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "",
                Duration.ofSeconds(60),
                Duration.ofSeconds(8)
        );
        service = new VideoGenerationService(
                resourceRepository, taskRepository, promptBuilder, klingClient, mockProperties,
                productService, synthesisWorker
        );
        when(promptBuilder.buildResourcePrompt(any(), any())).thenReturn("测试提示词");
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            VideoGenerationTask saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
    }

    @Test
    void withoutKlingKeyUsesLocalProviderAndTriggersWorker() {
        TourismResource resource = publishedResource();
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        VideoTaskResponse response = service.createForResource(
                7L, new VideoGenerateRequest(1L, 5, "16:9", "720p", null, List.of("aGVsbG8="))
        );

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.provider()).isEqualTo("LOCAL");
        assertThat(response.mock()).isFalse();
        verify(synthesisWorker).processTask(eq(response.id()), eq(List.of("aGVsbG8=")));
        verify(klingClient, never()).createTextToVideo(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void localProviderRequiresImages() {
        TourismResource resource = publishedResource();
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.createForResource(
                7L, new VideoGenerateRequest(1L, 5, "16:9", "720p", null, null)
        )).isInstanceOf(BusinessException.class).hasMessageContaining("至少需要一张图片");
    }

    @Test
    void withKlingKeySubmitsToKlingAndMovesToProcessing() {
        KlingProperties realProperties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "real-key",
                Duration.ofSeconds(60),
                Duration.ofSeconds(8)
        );
        service = new VideoGenerationService(
                resourceRepository, taskRepository, promptBuilder, klingClient, realProperties,
                productService, synthesisWorker
        );
        TourismResource resource = publishedResource();
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(klingClient.createImageToVideo(any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new KlingClient.CreateTaskResult("kling-123", "submitted"));

        VideoTaskResponse response = service.createForResource(
                7L, new VideoGenerateRequest(1L, 5, "16:9", "720p", null, List.of("aGVsbG8="))
        );

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.provider()).isEqualTo("KLING");
        verify(synthesisWorker, never()).processTask(any(), any());
    }

    @Test
    void withKlingKeyKlingFailureFailsTaskWithoutThrowing() {
        KlingProperties realProperties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "real-key",
                Duration.ofSeconds(60),
                Duration.ofSeconds(8)
        );
        service = new VideoGenerationService(
                resourceRepository, taskRepository, promptBuilder, klingClient, realProperties,
                productService, synthesisWorker
        );
        TourismResource resource = publishedResource();
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(klingClient.createTextToVideo(any(), any(Integer.class), any(), any(), any()))
                .thenThrow(new BusinessException("可灵账户资源包已用完或过期，请到 kling.ai 控制台购买资源包或开通后付费。"));

        VideoTaskResponse response = service.createForResource(
                7L, new VideoGenerateRequest(1L, 5, "16:9", "720p", null, null)
        );

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.errorMessage()).contains("资源包已用完");
    }

    @Test
    void rejectsMissingOrUnpublishedResource() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createForResource(
                7L, new VideoGenerateRequest(1L, 5, "16:9", "720p", null, List.of("aGVsbG8="))
        )).isInstanceOf(BusinessException.class).hasMessageContaining("不存在或已下线");

        TourismPlace place = TourismPlace.verified(
                "杭州", "测试地点", "西湖区", "测试路1号", "POI-1",
                BigDecimal.valueOf(120.1), BigDecimal.valueOf(30.2), "高德"
        );
        TourismResource draft = TourismResource.draft(
                place, "草稿资源", TourismResource.Category.SCENIC, "介绍", "[]", "[]",
                "高德", "https://example.com", "09:00-17:00",
                BigDecimal.valueOf(10), BigDecimal.valueOf(20)
        );
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.createForResource(
                7L, new VideoGenerateRequest(2L, 5, "16:9", "720p", null, List.of("aGVsbG8="))
        )).isInstanceOf(BusinessException.class).hasMessageContaining("不存在或已下线");
    }

    @Test
    void merchantProductOwnershipIsEnforced() {
        when(productService.getProduct(9L, 55L))
                .thenThrow(new BusinessException("旅游产品不存在或不属于当前商家。"));
        assertThatThrownBy(() -> service.createForMerchant(
                9L, new MerchantVideoGenerateRequest(
                        MerchantVideoGenerateRequest.SourceType.PRODUCT, 55L, 5, "16:9", "720p", null,
                        List.of("aGVsbG8=")
                )
        )).isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前商家");
    }

    @Test
    void merchantResourceCreatesLocalTaskWithMerchantOwner() {
        TourismResource resource = publishedResource();
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        VideoTaskResponse response = service.createForMerchant(
                9L, new MerchantVideoGenerateRequest(
                        MerchantVideoGenerateRequest.SourceType.RESOURCE, 1L, 5, "16:9", "720p", null,
                        List.of("aGVsbG8=")
                )
        );

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.provider()).isEqualTo("LOCAL");
        verify(synthesisWorker).processTask(eq(response.id()), eq(List.of("aGVsbG8=")));
    }

    @Test
    void latestSucceededVideoUrlsGroupsNewestPerResource() {
        VideoGenerationTask older = pendingLocal(1L, 11L);
        VideoGenerationTask newer = pendingLocal(1L, 12L);
        VideoGenerationTask other = pendingLocal(2L, 13L);
        when(taskRepository.findByResourceIdInAndStatusOrderByUpdatedAtDesc(
                any(), eq(VideoGenerationTask.Status.SUCCEEDED)
        )).thenReturn(List.of(newer, older, other));

        Map<Long, String> urls = service.latestSucceededVideoUrls(List.of(1L, 2L));

        assertThat(urls).containsOnlyKeys(1L, 2L);
        assertThat(urls.get(1L)).isEqualTo("/api/tourism/videos/12/file");
        assertThat(urls.get(2L)).isEqualTo("/api/tourism/videos/13/file");
    }

    @Test
    void getVideoFileRequiresSucceededTaskWithExistingFile() throws Exception {
        VideoGenerationTask pending = pendingLocal(1L, 1L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.getVideoFile(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("尚未生成完成");

        Path tempFile = Files.createTempFile("vernflow-video-test", ".mp4");
        try {
            VideoGenerationTask succeeded = pendingLocal(1L, 2L);
            succeeded.markSucceeded(tempFile.toAbsolutePath().toString(), null);
            when(taskRepository.findById(2L)).thenReturn(Optional.of(succeeded));
            assertThat(service.getVideoFile(2L).filePath()).isEqualTo(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void getTaskMissingThrowsBusinessException() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTask(99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("视频任务不存在");
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

    private VideoGenerationTask pendingLocal(Long resourceId, Long taskId) {
        VideoGenerationTask task = VideoGenerationTask.pending(
                resourceId, null, 7L, VideoGenerationTask.TaskType.TEXT2VIDEO,
                VideoGenerationTask.Provider.LOCAL, "p", 5, "16:9", "720p", false
        );
        ReflectionTestUtils.setField(task, "id", taskId);
        return task;
    }
}
