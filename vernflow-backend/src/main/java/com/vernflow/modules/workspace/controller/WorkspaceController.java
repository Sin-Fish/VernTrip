package com.vernflow.modules.workspace.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceStateSnapshot;
import com.vernflow.modules.workspace.dto.WorkspaceStateUpdateRequest;
import com.vernflow.modules.workspace.service.WorkspaceBootstrapService;
import com.vernflow.modules.workspace.service.WorkspaceStateService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace-facing endpoints consumed by the VernFlow frontend shell.
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceBootstrapService workspaceBootstrapService;
    private final WorkspaceStateService workspaceStateService;
    private final UserAuthService userAuthService;

    public WorkspaceController(
            WorkspaceBootstrapService workspaceBootstrapService,
            WorkspaceStateService workspaceStateService,
            UserAuthService userAuthService
    ) {
        this.workspaceBootstrapService = workspaceBootstrapService;
        this.workspaceStateService = workspaceStateService;
        this.userAuthService = userAuthService;
    }

    /**
     * Load the initial workspace payload for the frontend shell.
     *
     * @return bootstrap payload
     */
    @GetMapping("/bootstrap")
    public ApiResponse<WorkspaceBootstrapResponse> bootstrap(HttpServletRequest request) {
        return ApiResponse.success(workspaceBootstrapService.loadBootstrap(userAuthService.currentUserId(request)));
    }

    /**
     * Persist the latest workspace-shell state.
     *
     * @param request update request
     * @return normalized persisted snapshot
     */
    @PutMapping("/state")
    public ApiResponse<WorkspaceStateSnapshot> updateState(
            HttpServletRequest servletRequest,
            @RequestBody WorkspaceStateUpdateRequest request
    ) {
        return ApiResponse.success(workspaceStateService.updateState(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }
}
