package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.TourismResourceResponse;
import com.vernflow.modules.tourism.dto.TourismRecommendationRequest;
import com.vernflow.modules.tourism.dto.TourismRecommendationResponse;
import com.vernflow.modules.tourism.dto.TravelConversationRequest;
import com.vernflow.modules.tourism.dto.TravelConversationResponse;
import com.vernflow.modules.tourism.dto.TravelPlanAdjustRequest;
import com.vernflow.modules.tourism.dto.TravelPlanCreateRequest;
import com.vernflow.modules.tourism.dto.TravelPlanResponse;
import com.vernflow.modules.tourism.service.TravelPlanningService;
import com.vernflow.modules.tourism.service.TravelConversationService;
import com.vernflow.modules.tourism.service.TourismRecommendationService;
import com.vernflow.modules.tourism.service.TravelFortuneService;
import com.vernflow.modules.tourism.dto.FortuneDrawRequest;
import com.vernflow.modules.tourism.dto.FortuneDrawResponse;
import com.vernflow.modules.tourism.dto.FortuneProfileRequest;
import com.vernflow.modules.tourism.dto.FortuneProfileResponse;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tourism")
public class TourismController {

    private final TravelPlanningService travelPlanningService;
    private final TravelConversationService travelConversationService;
    private final TourismRecommendationService tourismRecommendationService;
    private final UserAuthService userAuthService;
    private final TravelFortuneService travelFortuneService;

    public TourismController(
            TravelPlanningService travelPlanningService,
            TravelConversationService travelConversationService,
            TourismRecommendationService tourismRecommendationService,
            TravelFortuneService travelFortuneService,
            UserAuthService userAuthService
    ) {
        this.travelPlanningService = travelPlanningService;
        this.travelConversationService = travelConversationService;
        this.tourismRecommendationService = tourismRecommendationService;
        this.userAuthService = userAuthService;
        this.travelFortuneService = travelFortuneService;
    }

    @GetMapping("/fortune/profile")
    public ApiResponse<FortuneProfileResponse> fortuneProfile(HttpServletRequest request) {
        return ApiResponse.success(travelFortuneService.profile(userAuthService.currentUserId(request)));
    }

    @PutMapping("/fortune/profile")
    public ApiResponse<FortuneProfileResponse> saveFortuneProfile(HttpServletRequest servletRequest,
            @Valid @RequestBody FortuneProfileRequest request) {
        return ApiResponse.success(travelFortuneService.saveProfile(userAuthService.currentUserId(servletRequest), request));
    }

    @PostMapping("/fortune")
    public ApiResponse<FortuneDrawResponse> drawFortune(HttpServletRequest servletRequest,
            @Valid @RequestBody FortuneDrawRequest request) {
        return ApiResponse.success(travelFortuneService.draw(userAuthService.currentUserId(servletRequest), request));
    }

    @PostMapping("/conversations/analyze")
    public ApiResponse<TravelConversationResponse> analyzeConversation(
            HttpServletRequest servletRequest,
            @Valid @RequestBody TravelConversationRequest request
    ) {
        return ApiResponse.success(travelConversationService.respond(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    @GetMapping("/resources")
    public ApiResponse<List<TourismResourceResponse>> resources(
            @RequestParam(defaultValue = "杭州") String city
    ) {
        return ApiResponse.success(travelPlanningService.listResources(city));
    }

    @PostMapping("/recommendations")
    public ApiResponse<TourismRecommendationResponse> recommendations(
            HttpServletRequest servletRequest,
            @Valid @RequestBody TourismRecommendationRequest request
    ) {
        return ApiResponse.success(tourismRecommendationService.recommend(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    @PostMapping("/trips/plan")
    public ApiResponse<TravelPlanResponse> createPlan(
            HttpServletRequest servletRequest,
            @Valid @RequestBody TravelPlanCreateRequest request
    ) {
        return ApiResponse.success(travelPlanningService.createPlan(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    @GetMapping("/plans/{planId}")
    public ApiResponse<TravelPlanResponse> plan(
            HttpServletRequest request,
            @PathVariable Long planId
    ) {
        return ApiResponse.success(travelPlanningService.getPlan(
                userAuthService.currentUserId(request),
                planId
        ));
    }

    @PostMapping("/plans/{planId}/adjust")
    public ApiResponse<TravelPlanResponse> adjustPlan(
            HttpServletRequest servletRequest,
            @PathVariable Long planId,
            @Valid @RequestBody TravelPlanAdjustRequest request
    ) {
        return ApiResponse.success(travelPlanningService.adjustPlan(
                userAuthService.currentUserId(servletRequest),
                planId,
                request
        ));
    }

    @PostMapping("/plans/{planId}/save")
    public ApiResponse<TravelPlanResponse> savePlan(
            HttpServletRequest request,
            @PathVariable Long planId
    ) {
        return ApiResponse.success(travelPlanningService.savePlan(
                userAuthService.currentUserId(request),
                planId
        ));
    }

    @DeleteMapping("/plans/{planId}/save")
    public ApiResponse<Void> removeSavedPlan(
            HttpServletRequest request,
            @PathVariable Long planId
    ) {
        travelPlanningService.removeSavedPlan(userAuthService.currentUserId(request), planId);
        return ApiResponse.success("removed", null);
    }

    @GetMapping("/plans")
    public ApiResponse<List<TravelPlanResponse>> savedPlans(HttpServletRequest request) {
        return ApiResponse.success(travelPlanningService.listSavedPlans(
                userAuthService.currentUserId(request)
        ));
    }
}
