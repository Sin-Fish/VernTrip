package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceCreateRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceStatusRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceUpdateRequest;
import com.vernflow.modules.tourism.service.MerchantResourceService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/resources")
public class MerchantResourceController {

    private final MerchantResourceService merchantResourceService;

    public MerchantResourceController(MerchantResourceService merchantResourceService) {
        this.merchantResourceService = merchantResourceService;
    }

    @GetMapping
    public ApiResponse<List<MerchantResourceResponse>> resources() {
        return ApiResponse.success(merchantResourceService.listResources());
    }

    @PostMapping
    public ApiResponse<MerchantResourceResponse> createResource(
            @Valid @RequestBody MerchantResourceCreateRequest request
    ) {
        return ApiResponse.success(merchantResourceService.createResource(request));
    }

    @PutMapping("/{resourceId}")
    public ApiResponse<MerchantResourceResponse> updateResource(
            @PathVariable Long resourceId,
            @Valid @RequestBody MerchantResourceUpdateRequest request
    ) {
        return ApiResponse.success(merchantResourceService.updateResource(resourceId, request));
    }

    @PatchMapping("/{resourceId}/status")
    public ApiResponse<MerchantResourceResponse> changeStatus(
            @PathVariable Long resourceId,
            @Valid @RequestBody MerchantResourceStatusRequest request
    ) {
        return ApiResponse.success(merchantResourceService.changeStatus(resourceId, request.status()));
    }
}
