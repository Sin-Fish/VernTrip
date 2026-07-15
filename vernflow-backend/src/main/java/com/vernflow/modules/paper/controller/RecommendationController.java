package com.vernflow.modules.paper.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.common.response.PageResponse;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.service.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/random")
    public ApiResponse<List<ExternalPaperResponse>> listRandomRecommendations(
            @RequestParam(defaultValue = "6") int size
    ) {
        return ApiResponse.success(recommendationService.listRandomRecommendations(size));
    }

    @PostMapping("/{recommendationId}/import")
    public ApiResponse<ExternalPaperResponse> importRecommendation(@PathVariable Long recommendationId) {
        return ApiResponse.success("推荐已标记为已引入", recommendationService.importRecommendation(recommendationId));
    }

    @PostMapping("/{recommendationId}/ignore")
    public ApiResponse<ExternalPaperResponse> ignoreRecommendation(@PathVariable Long recommendationId) {
        return ApiResponse.success("推荐已忽略", recommendationService.ignoreRecommendation(recommendationId));
    }
}

