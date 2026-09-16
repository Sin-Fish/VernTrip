package com.vernflow.modules.tourism.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceAuditResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceCreateRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceStatusRequest;
import com.vernflow.modules.tourism.dto.MerchantResourceUpdateRequest;
import com.vernflow.modules.tourism.service.MerchantResourceAuditService;
import com.vernflow.modules.tourism.service.MerchantResourceService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/resources")
public class MerchantResourceController {

    private final MerchantResourceService merchantResourceService;
    private final MerchantResourceAuditService merchantResourceAuditService;

    public MerchantResourceController(
            MerchantResourceService merchantResourceService,
            MerchantResourceAuditService merchantResourceAuditService
    ) {
        this.merchantResourceService = merchantResourceService;
        this.merchantResourceAuditService = merchantResourceAuditService;
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

    @PostMapping("/audit")
    public ApiResponse<MerchantResourceAuditResponse> auditResource(
            @RequestAttribute(UserAuthService.REQUEST_ATTR_USER_ID) Long merchantUserId,
            @Valid @RequestBody MerchantResourceCreateRequest request
    ) {
        return ApiResponse.success(merchantResourceAuditService.audit(merchantUserId, request));
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
