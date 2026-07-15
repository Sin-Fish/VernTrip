package com.vernflow.modules.workspace.controller;

import com.vernflow.modules.workspace.dto.WorkspaceFileDescriptor;
import com.vernflow.modules.workspace.dto.WorkspaceFinderCreateRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderDeleteRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderListResponse;
import com.vernflow.modules.workspace.dto.WorkspaceFinderRenameRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderSearchResponse;
import com.vernflow.modules.workspace.service.WorkspaceFinderService;
import com.vernflow.modules.user.service.UserAuthService;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * Generic workspace endpoints consumed by VueFinder.
 */
@RestController
@RequestMapping("/api/finder")
public class WorkspaceFinderController {

    private final WorkspaceFinderService workspaceFinderService;
    private final UserAuthService userAuthService;

    public WorkspaceFinderController(
            WorkspaceFinderService workspaceFinderService,
            UserAuthService userAuthService
    ) {
        this.workspaceFinderService = workspaceFinderService;
        this.userAuthService = userAuthService;
    }

    @GetMapping
    public WorkspaceFinderListResponse list(
            HttpServletRequest request,
            @RequestParam(required = false) String path
    ) {
        return workspaceFinderService.list(userAuthService.currentUserId(request), path);
    }

    @GetMapping("/search")
    public WorkspaceFinderSearchResponse search(
            HttpServletRequest request,
            @RequestParam(required = false) String path,
            @RequestParam(required = false, defaultValue = "") String filter
    ) {
        return workspaceFinderService.search(userAuthService.currentUserId(request), filter);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceFinderListResponse upload(
            HttpServletRequest request,
            @RequestParam(required = false) String path,
            @RequestParam("file") MultipartFile file
    ) {
        return workspaceFinderService.upload(userAuthService.currentUserId(request), path, file);
    }

    @PostMapping("/create-file")
    public WorkspaceFinderListResponse createFile(
            HttpServletRequest servletRequest,
            @RequestBody WorkspaceFinderCreateRequest request
    ) {
        return workspaceFinderService.createFile(userAuthService.currentUserId(servletRequest), request);
    }

    @PostMapping("/create-folder")
    public WorkspaceFinderListResponse createFolder(
            HttpServletRequest servletRequest,
            @RequestBody WorkspaceFinderCreateRequest request
    ) {
        return workspaceFinderService.createFolder(userAuthService.currentUserId(servletRequest), request);
    }

    @PostMapping("/rename")
    public WorkspaceFinderListResponse rename(
            HttpServletRequest servletRequest,
            @RequestBody WorkspaceFinderRenameRequest request
    ) {
        return workspaceFinderService.rename(userAuthService.currentUserId(servletRequest), request);
    }

    @PostMapping("/delete")
    public WorkspaceFinderListResponse delete(
            HttpServletRequest servletRequest,
            @RequestBody WorkspaceFinderDeleteRequest request
    ) {
        return workspaceFinderService.delete(userAuthService.currentUserId(servletRequest), request);
    }

    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(HttpServletRequest request, @RequestParam String path) {
        return fileResponse(workspaceFinderService.resolveFile(userAuthService.currentUserId(request), path), true);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(HttpServletRequest request, @RequestParam String path) {
        return fileResponse(workspaceFinderService.resolveFile(userAuthService.currentUserId(request), path), false);
    }

    private ResponseEntity<Resource> fileResponse(WorkspaceFileDescriptor descriptor, boolean inline) {
        Resource resource = new FileSystemResource(descriptor.filePath());
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline()
                .filename(descriptor.originalFileName(), StandardCharsets.UTF_8)
                .build()
                : ContentDisposition.attachment()
                .filename(descriptor.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(descriptor.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }
}
