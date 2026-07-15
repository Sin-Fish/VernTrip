package com.vernflow.modules.workbench.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.user.service.UserAuthService;
import com.vernflow.modules.workbench.dto.WorkbenchRunCreateRequest;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;
import com.vernflow.modules.workbench.service.WorkbenchRunService;
import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;

import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Workbench task endpoints for the new workspace-first pipeline.
 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchRunService workbenchRunService;
    private final UserAuthService userAuthService;

    public WorkbenchController(
            WorkbenchRunService workbenchRunService,
            UserAuthService userAuthService
    ) {
        this.workbenchRunService = workbenchRunService;
        this.userAuthService = userAuthService;
    }

    /**
     * List recent workbench runs for the workbench table.
     *
     * @return recent run summaries
     */
    @GetMapping("/runs")
    public ApiResponse<List<WorkspaceBootstrapResponse.WorkbenchRecord>> listRuns(HttpServletRequest request) {
        return ApiResponse.success(workbenchRunService.listWorkbenchRecords(userAuthService.currentUserId(request)));
    }

    /**
     * Create a new workbench run from the current workspace scope.
     *
     * @param request create request
     * @return created run detail
     */
    @PostMapping("/runs")
    public ApiResponse<WorkbenchRunDetailResponse> createRun(
            HttpServletRequest servletRequest,
            @RequestBody WorkbenchRunCreateRequest request
    ) {
        return ApiResponse.success(workbenchRunService.createRun(
                userAuthService.currentUserId(servletRequest),
                request
        ));
    }

    /**
     * Update the currently open workbench run instead of creating a duplicate history row.
     *
     * @param request updated editor state
     * @return updated run detail
     */
    @PutMapping("/runs/{runId}")
    public ApiResponse<WorkbenchRunDetailResponse> updateRun(
            HttpServletRequest servletRequest,
            @PathVariable Long runId,
            @RequestBody WorkbenchRunCreateRequest request
    ) {
        return ApiResponse.success(workbenchRunService.updateRun(
                userAuthService.currentUserId(servletRequest),
                runId,
                request
        ));
    }

    /**
     * Load one persisted workbench run.
     *
     * @param runId run id
     * @return run detail
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<WorkbenchRunDetailResponse> getRun(
            HttpServletRequest request,
            @PathVariable Long runId,
            @RequestParam(name = "page", required = false) Integer page
    ) {
        return ApiResponse.success(workbenchRunService.loadRunDetail(
                userAuthService.currentUserId(request),
                runId,
                page
        ));
    }

    @DeleteMapping("/runs/{runId}")
    public ApiResponse<Boolean> deleteRun(
            HttpServletRequest request,
            @PathVariable Long runId
    ) {
        workbenchRunService.deleteRun(userAuthService.currentUserId(request), runId);
        return ApiResponse.success(true);
    }
}
