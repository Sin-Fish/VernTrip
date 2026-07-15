package com.vernflow.modules.admin.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.common.response.PageResponse;
import com.vernflow.modules.admin.dto.AdminParseTaskSummaryResponse;
import com.vernflow.modules.admin.service.AdminConsoleService;
import com.vernflow.modules.paper.dto.AdminRelationResponse;
import com.vernflow.modules.paper.dto.PaperDetailResponse;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;
import com.vernflow.modules.paper.service.PaperCommandService;
import com.vernflow.modules.paper.service.PaperQueryService;
import com.vernflow.modules.paper.service.PaperRelationService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/paper")
public class AdminPaperGovernanceController {

    private final AdminConsoleService adminConsoleService;
    private final PaperQueryService paperQueryService;
    private final PaperCommandService paperCommandService;
    private final PaperRelationService paperRelationService;

    public AdminPaperGovernanceController(
            AdminConsoleService adminConsoleService,
            PaperQueryService paperQueryService,
            PaperCommandService paperCommandService,
            PaperRelationService paperRelationService
    ) {
        this.adminConsoleService = adminConsoleService;
        this.paperQueryService = paperQueryService;
        this.paperCommandService = paperCommandService;
        this.paperRelationService = paperRelationService;
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<AdminParseTaskSummaryResponse>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(adminConsoleService.listParseTasks(page, size));
    }

    @PostMapping("/tasks/{taskId}/front")
    public ApiResponse<Void> moveTaskToFront(@PathVariable Long taskId) {
        paperCommandService.moveTaskToFront(taskId);
        return ApiResponse.success("Task moved to the front of the queue.", null);
    }

    @PostMapping("/tasks/{taskId}/pause")
    public ApiResponse<Void> pauseTask(@PathVariable Long taskId) {
        paperCommandService.pauseTask(taskId);
        return ApiResponse.success("Task paused.", null);
    }

    @PostMapping("/tasks/{taskId}/resume")
    public ApiResponse<Void> resumeTask(@PathVariable Long taskId) {
        paperCommandService.resumeTask(taskId);
        return ApiResponse.success("Task resumed.", null);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<Void> retryTask(@PathVariable Long taskId) {
        paperCommandService.retryTask(taskId);
        return ApiResponse.success("Task queued for retry.", null);
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable Long taskId) {
        paperCommandService.deleteTask(taskId);
        return ApiResponse.success("Task deleted.", null);
    }

    @PutMapping("/papers/{paperId}")
    public ApiResponse<PaperDetailResponse> updatePaper(
            @PathVariable Long paperId,
            @RequestBody UpdatePaperRequest request
    ) {
        return ApiResponse.success("Paper metadata updated.", paperCommandService.updatePaper(paperId, request));
    }

    @GetMapping("/relations")
    public ApiResponse<PageResponse<AdminRelationResponse>> listRelations(
            @RequestParam(required = false) Long paperId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(paperQueryService.listRelations(paperId, page, size));
    }

    @PostMapping("/relations/rebuild")
    public ApiResponse<Void> rebuildRelations() {
        paperRelationService.rebuildAllRelations();
        return ApiResponse.success("Paper relations rebuilt.", null);
    }
}
