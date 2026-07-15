package com.vernflow.modules.admin.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.common.response.PageResponse;
import com.vernflow.modules.admin.dto.AdminDashboardResponse;
import com.vernflow.modules.admin.dto.AdminOperationLogResponse;
import com.vernflow.modules.admin.dto.AdminSessionSummaryResponse;
import com.vernflow.modules.admin.dto.AdminStorageCreateRequest;
import com.vernflow.modules.admin.dto.AdminStorageDeleteRequest;
import com.vernflow.modules.admin.dto.AdminStorageListResponse;
import com.vernflow.modules.admin.dto.AdminSystemOverviewResponse;
import com.vernflow.modules.admin.dto.AdminUserSummaryResponse;
import com.vernflow.modules.admin.dto.AdminWorkbenchRunSummaryResponse;
import com.vernflow.modules.admin.service.AdminAuthService;
import com.vernflow.modules.admin.service.AdminConsoleService;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;

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
@RequestMapping("/api/admin")
public class AdminConsoleController {

    private final AdminConsoleService adminConsoleService;
    private final AdminAuthService adminAuthService;

    public AdminConsoleController(
            AdminConsoleService adminConsoleService,
            AdminAuthService adminAuthService
    ) {
        this.adminConsoleService = adminConsoleService;
        this.adminAuthService = adminAuthService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.success(adminConsoleService.loadDashboard());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserSummaryResponse>> listUsers() {
        return ApiResponse.success(adminConsoleService.listUsers());
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<AdminUserSummaryResponse> userDetail(@PathVariable Long userId) {
        return ApiResponse.success(adminConsoleService.getUser(userId));
    }

    @GetMapping("/storage")
    public ApiResponse<AdminStorageListResponse> listStorage(
            @RequestParam(required = false, defaultValue = "/") String path
    ) {
        return ApiResponse.success(adminConsoleService.listStorage(path));
    }

    @GetMapping("/users/{userId}/storage")
    public ApiResponse<AdminStorageListResponse> listUserStorage(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "/") String path
    ) {
        return ApiResponse.success(adminConsoleService.listUserStorage(userId, path));
    }

    @PostMapping("/storage/folders")
    public ApiResponse<AdminStorageListResponse> createFolder(
            @Valid @RequestBody AdminStorageCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminConsoleService.createFolder(
                request,
                adminAuthService.currentUsername(servletRequest)
        ));
    }

    @PostMapping("/users/{userId}/storage/folders")
    public ApiResponse<AdminStorageListResponse> createUserFolder(
            @PathVariable Long userId,
            @Valid @RequestBody AdminStorageCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminConsoleService.createUserFolder(
                userId,
                request,
                adminAuthService.currentUsername(servletRequest)
        ));
    }

    @DeleteMapping("/storage")
    public ApiResponse<AdminStorageListResponse> deleteStorage(
            @Valid @RequestBody AdminStorageDeleteRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminConsoleService.deleteStorage(
                request,
                adminAuthService.currentUsername(servletRequest)
        ));
    }

    @DeleteMapping("/users/{userId}/storage")
    public ApiResponse<AdminStorageListResponse> deleteUserStorage(
            @PathVariable Long userId,
            @Valid @RequestBody AdminStorageDeleteRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminConsoleService.deleteUserStorage(
                userId,
                request,
                adminAuthService.currentUsername(servletRequest)
        ));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<AdminSessionSummaryResponse>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listAssistantSessions(page, size));
    }

    @GetMapping("/users/{userId}/sessions")
    public ApiResponse<PageResponse<AdminSessionSummaryResponse>> listUserSessions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listAssistantSessions(userId, page, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AssistantSessionDetailResponse> sessionDetail(@PathVariable Long sessionId) {
        return ApiResponse.success(adminConsoleService.getAssistantSession(sessionId));
    }

    @GetMapping("/users/{userId}/sessions/{sessionId}")
    public ApiResponse<AssistantSessionDetailResponse> userSessionDetail(
            @PathVariable Long userId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(adminConsoleService.getAssistantSession(userId, sessionId));
    }

    @GetMapping("/workbench/runs")
    public ApiResponse<PageResponse<AdminWorkbenchRunSummaryResponse>> listWorkbenchRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listWorkbenchRuns(page, size));
    }

    @GetMapping("/users/{userId}/workbench/runs")
    public ApiResponse<PageResponse<AdminWorkbenchRunSummaryResponse>> listUserWorkbenchRuns(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listWorkbenchRuns(userId, page, size));
    }

    @GetMapping("/workbench/runs/{runId}")
    public ApiResponse<WorkbenchRunDetailResponse> workbenchRunDetail(@PathVariable Long runId) {
        return ApiResponse.success(adminConsoleService.getWorkbenchRun(runId));
    }

    @GetMapping("/users/{userId}/workbench/runs/{runId}")
    public ApiResponse<WorkbenchRunDetailResponse> userWorkbenchRunDetail(
            @PathVariable Long userId,
            @PathVariable Long runId
    ) {
        return ApiResponse.success(adminConsoleService.getWorkbenchRun(userId, runId));
    }

    @GetMapping("/system")
    public ApiResponse<AdminSystemOverviewResponse> systemOverview() {
        return ApiResponse.success(adminConsoleService.loadSystemOverview());
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<AdminOperationLogResponse>> listLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listOperationLogs(page, size));
    }
}
