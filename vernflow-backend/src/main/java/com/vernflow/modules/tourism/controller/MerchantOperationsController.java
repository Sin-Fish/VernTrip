package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.MerchantContentGenerateRequest;
import com.vernflow.modules.tourism.dto.MerchantContentGenerateResponse;
import com.vernflow.modules.tourism.dto.MerchantDashboardResponse;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;
import com.vernflow.modules.tourism.dto.MerchantProductStatusRequest;
import com.vernflow.modules.tourism.dto.MerchantProductUpsertRequest;
import com.vernflow.modules.tourism.service.MerchantContentService;
import com.vernflow.modules.tourism.service.MerchantDashboardService;
import com.vernflow.modules.tourism.service.MerchantProductService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant")
public class MerchantOperationsController {

    private final MerchantDashboardService dashboardService;
    private final MerchantProductService productService;
    private final MerchantContentService contentService;

    public MerchantOperationsController(
            MerchantDashboardService dashboardService,
            MerchantProductService productService,
            MerchantContentService contentService
    ) {
        this.dashboardService = dashboardService;
        this.productService = productService;
        this.contentService = contentService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<MerchantDashboardResponse> dashboard(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId
    ) {
        return ApiResponse.success(dashboardService.dashboard(merchantUserId));
    }

    @GetMapping("/products")
    public ApiResponse<List<MerchantProductResponse>> products(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId
    ) {
        return ApiResponse.success(productService.listProducts(merchantUserId));
    }

    @PostMapping("/products")
    public ApiResponse<MerchantProductResponse> createProduct(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @Valid @RequestBody MerchantProductUpsertRequest request
    ) {
        return ApiResponse.success(productService.createProduct(merchantUserId, request));
    }

    @PutMapping("/products/{productId}")
    public ApiResponse<MerchantProductResponse> updateProduct(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @PathVariable Long productId,
            @Valid @RequestBody MerchantProductUpsertRequest request
    ) {
        return ApiResponse.success(productService.updateProduct(merchantUserId, productId, request));
    }

    @PatchMapping("/products/{productId}/status")
    public ApiResponse<MerchantProductResponse> changeProductStatus(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @PathVariable Long productId,
            @Valid @RequestBody MerchantProductStatusRequest request
    ) {
        return ApiResponse.success(productService.changeStatus(merchantUserId, productId, request.status()));
    }

    @PostMapping("/content/generate")
    public ApiResponse<MerchantContentGenerateResponse> generateContent(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @Valid @RequestBody MerchantContentGenerateRequest request
    ) {
        return ApiResponse.success(contentService.generate(merchantUserId, request));
    }
}
