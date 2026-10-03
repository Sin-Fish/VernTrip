package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.MerchantVideoGenerateRequest;
import com.vernflow.modules.tourism.dto.VideoTaskResponse;
import com.vernflow.modules.tourism.service.VideoGenerationService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家端 AI 营销宣传视频。商家身份由 UserAuthFilter 校验（/api/merchant 前缀 + MERCHANT 角色）。
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantVideoController {

    private final VideoGenerationService videoGenerationService;

    public MerchantVideoController(VideoGenerationService videoGenerationService) {
        this.videoGenerationService = videoGenerationService;
    }

    @PostMapping("/videos/generate")
    public ApiResponse<VideoTaskResponse> generate(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @Valid @RequestBody MerchantVideoGenerateRequest request
    ) {
        return ApiResponse.success(videoGenerationService.createForMerchant(merchantUserId, request));
    }

    @GetMapping("/videos")
    public ApiResponse<List<VideoTaskResponse>> history(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId
    ) {
        return ApiResponse.success(videoGenerationService.listMerchantVideos(merchantUserId));
    }
}
