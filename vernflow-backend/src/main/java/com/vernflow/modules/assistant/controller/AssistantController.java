package com.vernflow.modules.assistant.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.assistant.dto.AssistantAskRequest;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.assistant.dto.AssistantSessionSummaryResponse;
import com.vernflow.modules.assistant.service.WorkspaceAssistantService;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Workspace assistant conversation endpoints.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final WorkspaceAssistantService workspaceAssistantService;
    private final UserAuthService userAuthService;

    public AssistantController(
            WorkspaceAssistantService workspaceAssistantService,
            UserAuthService userAuthService
    ) {
        this.workspaceAssistantService = workspaceAssistantService;
        this.userAuthService = userAuthService;
    }

    /**
     * List saved assistant sessions.
     *
     * @return session cards
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AssistantSessionSummaryResponse>> listSessions(HttpServletRequest request) {
        return ApiResponse.success(workspaceAssistantService.listSessions(userAuthService.currentUserId(request)));
    }

    /**
     * Load a full assistant session.
     *
     * @param sessionId session id
     * @return full session detail
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AssistantSessionDetailResponse> getSession(
            HttpServletRequest request,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(workspaceAssistantService.getSession(
                userAuthService.currentUserId(request),
                sessionId
        ));
    }

    /**
     * Submit a prompt against the current workspace scope.
     *
     * @param request prompt and scope snapshot
     * @return updated session detail
     */
    @PostMapping("/sessions/ask")
    public ApiResponse<AssistantSessionDetailResponse> ask(
            HttpServletRequest servletRequest,
            @RequestBody AssistantAskRequest request
    ) {
        return ApiResponse.success(workspaceAssistantService.ask(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Boolean> deleteSession(
            HttpServletRequest request,
            @PathVariable Long sessionId
    ) {
        workspaceAssistantService.deleteSession(userAuthService.currentUserId(request), sessionId);
        return ApiResponse.success(true);
    }
}
