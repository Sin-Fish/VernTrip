package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.VideoFileDescriptor;
import com.vernflow.modules.tourism.dto.VideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoTaskResponse;
import com.vernflow.modules.tourism.service.VideoGenerationService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 游客端 AI 景点导览视频。
 * 创建与状态查询挂在受保护前缀 /api/me 下（要求登录，见 UserAuthFilter.PROTECTED_PREFIXES）；
 * 视频文件端点保持公开，因为 &lt;video :src&gt; 无法携带 Bearer 头。
 */
@RestController
@RequestMapping("/api")
public class TourismVideoController {

    private final VideoGenerationService videoGenerationService;
    private final UserAuthService userAuthService;

    public TourismVideoController(
            VideoGenerationService videoGenerationService,
            UserAuthService userAuthService
    ) {
        this.videoGenerationService = videoGenerationService;
        this.userAuthService = userAuthService;
    }

    @PostMapping("/me/videos")
    public ApiResponse<VideoTaskResponse> createResourceVideo(
            HttpServletRequest servletRequest,
            @Valid @RequestBody VideoGenerateRequest request
    ) {
        return ApiResponse.success(videoGenerationService.createForResource(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    @GetMapping("/me/video-tasks/{taskId}")
    public ApiResponse<VideoTaskResponse> taskStatus(
            HttpServletRequest servletRequest,
            @PathVariable Long taskId
    ) {
        userAuthService.currentUserId(servletRequest);
        return ApiResponse.success(videoGenerationService.getTask(taskId));
    }

    @GetMapping("/tourism/videos/{taskId}/file")
    public ResponseEntity<Resource> videoFile(@PathVariable Long taskId) {
        VideoFileDescriptor descriptor = videoGenerationService.getVideoFile(taskId);
        Resource resource = new FileSystemResource(descriptor.filePath());
        MediaType mediaType = MediaTypeFactory.getMediaType(descriptor.fileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(descriptor.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }
}
