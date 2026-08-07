package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.TourismResourceResponse;
import com.vernflow.modules.tourism.dto.TravelConversationRequest;
import com.vernflow.modules.tourism.dto.TravelConversationResponse;
import com.vernflow.modules.tourism.dto.TravelPlanAdjustRequest;
import com.vernflow.modules.tourism.dto.TravelPlanCreateRequest;
import com.vernflow.modules.tourism.dto.TravelPlanResponse;
import com.vernflow.modules.tourism.service.TravelPlanningService;
import com.vernflow.modules.tourism.service.TravelConversationService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final UserAuthService userAuthService;

    public TourismController(
            TravelPlanningService travelPlanningService,
            TravelConversationService travelConversationService,
            UserAuthService userAuthService
    ) {
        this.travelPlanningService = travelPlanningService;
        this.travelConversationService = travelConversationService;
        this.userAuthService = userAuthService;
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
