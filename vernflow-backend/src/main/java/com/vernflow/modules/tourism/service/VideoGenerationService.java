package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.KlingProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.kling.KlingClient;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;
import com.vernflow.modules.tourism.dto.MerchantVideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoFileDescriptor;
import com.vernflow.modules.tourism.dto.VideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoTaskResponse;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 视频生成编排。
 * 未配置 KLING_API_KEY 时走 AI 成片（provider=LOCAL）：真实图片 + AI 解说词 + AI 配音 + 自动剪辑，
 * 由 {@link VideoSynthesisWorker} 异步合成；配置了可灵 Key 时走真实 AI 视频（provider=KLING）。
 */
@Service
public class VideoGenerationService {

    public static final String VIDEO_FILE_URL_PATTERN = "/api/tourism/videos/%d/file";

    private final TourismResourceRepository resourceRepository;
    private final VideoGenerationTaskRepository taskRepository;
    private final VideoPromptBuilder promptBuilder;
    private final KlingClient klingClient;
    private final KlingProperties klingProperties;
    private final MerchantProductService productService;
    private final VideoSynthesisWorker synthesisWorker;

    public VideoGenerationService(
            TourismResourceRepository resourceRepository,
            VideoGenerationTaskRepository taskRepository,
            VideoPromptBuilder promptBuilder,
            KlingClient klingClient,
            KlingProperties klingProperties,
            MerchantProductService productService,
            VideoSynthesisWorker synthesisWorker
    ) {
        this.resourceRepository = resourceRepository;
        this.taskRepository = taskRepository;
        this.promptBuilder = promptBuilder;
        this.klingClient = klingClient;
        this.klingProperties = klingProperties;
        this.productService = productService;
        this.synthesisWorker = synthesisWorker;
    }

    /** 游客端：为景点资源创建导览视频任务。 */
    @Transactional
    public VideoTaskResponse createForResource(Long userId, VideoGenerateRequest request) {
        TourismResource resource = resourceRepository.findById(request.resourceId())
                .filter(item -> item.getStatus() == TourismResource.PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("文旅资源不存在或已下线。"));
        boolean local = klingProperties.mockEnabled();
        List<String> images = request.images() == null ? List.of() : request.images().stream()
                .filter(StringUtils::hasText).toList();
        if (local && images.isEmpty()) {
            throw new BusinessException("AI 成片至少需要一张图片素材。");
        }
        String prompt = promptBuilder.buildResourcePrompt(resource, request.promptHint());
        VideoGenerationTask task = VideoGenerationTask.pending(
                resource.getId(),
                null,
                userId,
                images.isEmpty() ? VideoGenerationTask.TaskType.TEXT2VIDEO : VideoGenerationTask.TaskType.IMAGE2VIDEO,
                local ? VideoGenerationTask.Provider.LOCAL : VideoGenerationTask.Provider.KLING,
                prompt,
                request.durationSeconds(),
                request.aspectRatio(),
                request.resolution(),
                false
        );
        task = taskRepository.save(task);
        dispatch(task, images);
        return toResponse(task);
    }

    /** 商家端：基于资源或产品创建营销宣传视频任务。 */
    @Transactional
    public VideoTaskResponse createForMerchant(Long merchantUserId, MerchantVideoGenerateRequest request) {
        String prompt;
        Long resourceId = null;
        Long productId = null;
        if (request.sourceType() == MerchantVideoGenerateRequest.SourceType.PRODUCT) {
            MerchantProductResponse product = productService.getProduct(merchantUserId, request.sourceId());
            productId = product.id();
            prompt = promptBuilder.buildProductPrompt(product, request.promptHint());
        } else {
            TourismResource resource = resourceRepository.findById(request.sourceId())
                    .orElseThrow(() -> new BusinessException("用于生成视频的文旅资源不存在。"));
            resourceId = resource.getId();
            prompt = promptBuilder.buildResourcePrompt(resource, request.promptHint());
        }
        boolean local = klingProperties.mockEnabled();
        List<String> images = request.images() == null ? List.of() : request.images().stream()
                .filter(StringUtils::hasText).toList();
        if (local && images.isEmpty()) {
            throw new BusinessException("AI 成片至少需要一张图片素材。");
        }
        VideoGenerationTask task = VideoGenerationTask.pending(
                resourceId,
                productId,
                merchantUserId,
                images.isEmpty() ? VideoGenerationTask.TaskType.TEXT2VIDEO : VideoGenerationTask.TaskType.IMAGE2VIDEO,
                local ? VideoGenerationTask.Provider.LOCAL : VideoGenerationTask.Provider.KLING,
                prompt,
                request.durationSeconds(),
                request.aspectRatio(),
                request.resolution(),
                false
        );
        task = taskRepository.save(task);
        dispatch(task, images);
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public VideoTaskResponse getTask(Long taskId) {
        return toResponse(taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("视频任务不存在。")));
    }

    @Transactional(readOnly = true)
    public VideoFileDescriptor getVideoFile(Long taskId) {
        VideoGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("视频任务不存在。"));
        if (task.getStatus() != VideoGenerationTask.Status.SUCCEEDED || !StringUtils.hasText(task.getVideoPath())) {
            throw new BusinessException("视频尚未生成完成。");
        }
        Path filePath = Path.of(task.getVideoPath());
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException("视频文件不存在或已被清理。");
        }
        return new VideoFileDescriptor(filePath, "vernflow-video-" + task.getId() + ".mp4");
    }

    @Transactional(readOnly = true)
    public List<VideoTaskResponse> listMerchantVideos(Long merchantUserId) {
        return taskRepository.findTop20ByCreatedByOrderByCreatedAtDesc(merchantUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 批量取资源的最新成功视频 URL（供资源列表回填 videoUrl，避免 N+1）。 */
    @Transactional(readOnly = true)
    public Map<Long, String> latestSucceededVideoUrls(Collection<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (VideoGenerationTask task : taskRepository
                .findByResourceIdInAndStatusOrderByUpdatedAtDesc(resourceIds, VideoGenerationTask.Status.SUCCEEDED)) {
            if (task.getResourceId() != null) {
                result.putIfAbsent(task.getResourceId(), videoFileUrl(task));
            }
        }
        return result;
    }

    /** 根据 provider 分发：LOCAL 交异步合成 Worker；KLING 同步向可灵提交。 */
    private void dispatch(VideoGenerationTask task, List<String> images) {
        if (task.getProvider() == VideoGenerationTask.Provider.LOCAL) {
            synthesisWorker.processTask(task.getId(), images);
            return;
        }
        try {
            KlingClient.CreateTaskResult created = images.isEmpty()
                    ? klingClient.createTextToVideo(
                    task.getPrompt(),
                    task.getDurationSeconds(),
                    task.getAspectRatio(),
                    task.getResolution(),
                    "verntrip-" + task.getId()
            )
                    : klingClient.createImageToVideo(
                    task.getPrompt(),
                    images.get(0),
                    task.getDurationSeconds(),
                    task.getAspectRatio(),
                    task.getResolution(),
                    "verntrip-" + task.getId()
            );
            task.markProcessing(created.taskId());
        } catch (BusinessException exception) {
            task.markFailed(exception.getMessage());
        }
    }

    private VideoTaskResponse toResponse(VideoGenerationTask task) {
        return new VideoTaskResponse(
                task.getId(),
                task.getStatus().name(),
                task.getTaskType().name(),
                task.getProvider().name(),
                task.isMock(),
                task.getDurationSeconds(),
                task.getAspectRatio(),
                task.getResolution(),
                task.getStatus() == VideoGenerationTask.Status.SUCCEEDED ? videoFileUrl(task) : null,
                task.getErrorMessage(),
                task.getKlingVideoUrl(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private String videoFileUrl(VideoGenerationTask task) {
        return VIDEO_FILE_URL_PATTERN.formatted(task.getId());
    }
}
