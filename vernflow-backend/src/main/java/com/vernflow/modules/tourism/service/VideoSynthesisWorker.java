package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.integration.ai.AiVideoSynthesisClient;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.VideoGenerationTask;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.tourism.repository.VideoGenerationTaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AI 成片执行器：异步调用 vernflow-ai 合成视频并转存本地。
 * 任务为 LOCAL provider（未配置可灵 Key）时由 VideoGenerationService 触发。
 */
@Component
public class VideoSynthesisWorker {

    private static final Logger log = LoggerFactory.getLogger(VideoSynthesisWorker.class);

    private final VideoGenerationTaskRepository taskRepository;
    private final TourismResourceRepository resourceRepository;
    private final MerchantProductService productService;
    private final VideoPromptBuilder promptBuilder;
    private final AiVideoSynthesisClient synthesisClient;
    private final StorageProperties storageProperties;

    public VideoSynthesisWorker(
            VideoGenerationTaskRepository taskRepository,
            TourismResourceRepository resourceRepository,
            MerchantProductService productService,
            VideoPromptBuilder promptBuilder,
            AiVideoSynthesisClient synthesisClient,
            StorageProperties storageProperties
    ) {
        this.taskRepository = taskRepository;
        this.resourceRepository = resourceRepository;
        this.productService = productService;
        this.promptBuilder = promptBuilder;
        this.synthesisClient = synthesisClient;
        this.storageProperties = storageProperties;
    }

    @Async("videoTaskExecutor")
    public void processTask(Long taskId, List<String> images) {
        VideoGenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != VideoGenerationTask.Status.PENDING) {
            return;
        }
        try {
            task.markProcessing();
            taskRepository.save(task);
            AiVideoSynthesisClient.SynthesisRequest request = buildRequest(task, images);
            byte[] mp4 = synthesisClient.synthesizeVideo(request);
            Path target = storageProperties.mediaDir().resolve("video-" + task.getId() + ".mp4");
            Files.createDirectories(target.getParent());
            Files.write(target, mp4);
            task.markSucceeded(target.toAbsolutePath().toString(), null);
        } catch (Exception exception) {
            log.warn("AI 成片任务 {} 执行失败。", task.getId(), exception);
            task.markFailed("AI 成片生成失败：" + exception.getMessage());
        }
        taskRepository.save(task);
    }

    private AiVideoSynthesisClient.SynthesisRequest buildRequest(
            VideoGenerationTask task,
            List<String> images
    ) {
        if (task.getResourceId() != null) {
            TourismResource resource = resourceRepository.findById(task.getResourceId())
                    .orElseThrow(() -> new IllegalStateException("文旅资源不存在。"));
            return new AiVideoSynthesisClient.SynthesisRequest(
                    resource.getName(),
                    resource.getCity(),
                    resource.getCategory().name(),
                    promptBuilder.buildFactsText(resource),
                    images,
                    task.getAspectRatio()
            );
        }
        MerchantProductResponse product = productService.getProduct(task.getCreatedBy(), task.getProductId());
        return new AiVideoSynthesisClient.SynthesisRequest(
                product.name(),
                "",
                "EXPERIENCE",
                promptBuilder.buildProductFactsText(product),
                images,
                task.getAspectRatio()
        );
    }
}
