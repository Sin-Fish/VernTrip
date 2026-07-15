package com.vernflow.modules.rag.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.rag.dto.WorkspaceFileRelationGraphResponse;
import com.vernflow.modules.rag.service.WorkspaceFileRelationService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class WorkspaceFileRelationController {

    private final WorkspaceFileRelationService workspaceFileRelationService;
    private final UserAuthService userAuthService;

    public WorkspaceFileRelationController(
            WorkspaceFileRelationService workspaceFileRelationService,
            UserAuthService userAuthService
    ) {
        this.workspaceFileRelationService = workspaceFileRelationService;
        this.userAuthService = userAuthService;
    }

    @GetMapping("/relations")
    public ApiResponse<WorkspaceFileRelationGraphResponse> relations(
            HttpServletRequest request,
            @RequestParam String path,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return ApiResponse.success(workspaceFileRelationService.buildGraph(
                userAuthService.currentUserId(request),
                path,
                Math.max(1, Math.min(limit, 12))
        ));
    }
}
