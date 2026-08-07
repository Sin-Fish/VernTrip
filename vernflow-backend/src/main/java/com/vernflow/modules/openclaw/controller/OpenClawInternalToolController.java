package com.vernflow.modules.openclaw.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.openclaw.dto.OpenClawTourismCatalogRequest;
import com.vernflow.modules.openclaw.dto.OpenClawTourismCatalogResponse;
import com.vernflow.modules.openclaw.dto.OpenClawWorkspaceScopeResponse;
import com.vernflow.modules.openclaw.service.OpenClawTourismToolService;
import com.vernflow.modules.openclaw.service.OpenClawWorkspaceToolService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/openclaw/v1/tools")
public class OpenClawInternalToolController {

    public static final String SESSION_KEY_HEADER = "X-OpenClaw-Session-Key";

    private final OpenClawWorkspaceToolService workspaceToolService;
    private final OpenClawTourismToolService tourismToolService;

    public OpenClawInternalToolController(
            OpenClawWorkspaceToolService workspaceToolService,
            OpenClawTourismToolService tourismToolService
    ) {
        this.workspaceToolService = workspaceToolService;
        this.tourismToolService = tourismToolService;
    }

    @PostMapping("/workspace-scope-read")
    public ApiResponse<OpenClawWorkspaceScopeResponse> readWorkspaceScope(
            @RequestHeader(SESSION_KEY_HEADER) String sessionKey
    ) {
        return ApiResponse.success(workspaceToolService.readScope(sessionKey));
    }

    @PostMapping("/tourism-catalog-read")
    public ApiResponse<OpenClawTourismCatalogResponse> readTourismCatalog(
            @RequestHeader(SESSION_KEY_HEADER) String sessionKey,
            @Valid @RequestBody OpenClawTourismCatalogRequest request
    ) {
        return ApiResponse.success(tourismToolService.readCatalog(sessionKey, request.city()));
    }
}
