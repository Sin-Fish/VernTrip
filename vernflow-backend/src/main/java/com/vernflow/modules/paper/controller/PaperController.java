package com.vernflow.modules.paper.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.common.response.PageResponse;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.service.*;

import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PaperController {

    private final PaperCommandService paperCommandService;
    private final PaperQueryService paperQueryService;
    private final PaperQaService paperQaService;

    public PaperController(
            PaperCommandService paperCommandService,
            PaperQueryService paperQueryService,
            PaperQaService paperQaService
    ) {
        this.paperCommandService = paperCommandService;
        this.paperQueryService = paperQueryService;
        this.paperQaService = paperQaService;
    }

    @PostMapping("/papers/upload")
    public ApiResponse<PaperUploadResponse> uploadPapers(@RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.success("上传成功，解析任务已开始执行", paperCommandService.uploadPapers(files));
    }

    @GetMapping("/papers")
    public ApiResponse<PageResponse<PaperItemResponse>> listPapers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.success(paperQueryService.listPapers(keyword, year, page, size));
    }

    @GetMapping("/papers/{paperId}")
    public ApiResponse<PaperDetailResponse> getPaperDetail(@PathVariable Long paperId) {
        return ApiResponse.success(paperQueryService.getPaperDetail(paperId));
    }

    @GetMapping("/papers/{paperId}/graph")
    public ApiResponse<PaperGraphResponse> getPaperGraph(@PathVariable Long paperId) {
        return ApiResponse.success(paperQueryService.getPaperGraph(paperId));
    }

    @GetMapping("/papers/{paperId}/file")
    public ResponseEntity<Resource> previewPaperFile(@PathVariable Long paperId) {
        PaperFileDescriptor fileDescriptor = paperQueryService.getPaperFile(paperId);
        Resource resource = new FileSystemResource(fileDescriptor.filePath());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(fileDescriptor.originalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @PostMapping("/qa")
    public ApiResponse<QaResponse> askQuestion(@Valid @RequestBody QaRequest request) {
        return ApiResponse.success(paperQaService.ask(request));
    }

    @GetMapping("/qa/sessions")
    public ApiResponse<List<QaSessionSummaryResponse>> listQaSessions(@RequestParam(required = false) Long paperId) {
        return ApiResponse.success(paperQaService.listSessions(paperId));
    }

    @PostMapping("/qa/sessions")
    public ApiResponse<QaSessionDetailResponse> saveQaSession(@RequestBody QaSessionSaveRequest request) {
        return ApiResponse.success("问答会话已保存", paperQaService.saveSession(request));
    }

    @GetMapping("/qa/sessions/{sessionId}")
    public ApiResponse<QaSessionDetailResponse> getQaSession(@PathVariable Long sessionId) {
        return ApiResponse.success(paperQaService.getSession(sessionId));
    }

    @DeleteMapping("/qa/sessions/{sessionId}")
    public ApiResponse<Void> deleteQaSession(@PathVariable Long sessionId) {
        paperQaService.deleteSession(sessionId);
        return ApiResponse.success("问答会话已删除", null);
    }
}

