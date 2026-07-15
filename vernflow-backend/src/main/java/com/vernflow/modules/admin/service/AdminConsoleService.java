package com.vernflow.modules.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.AdminProperties;
import com.vernflow.common.config.AiServiceProperties;
import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.common.response.PageResponse;
import com.vernflow.modules.admin.domain.AdminOperationLog;
import com.vernflow.modules.admin.dto.AdminDashboardResponse;
import com.vernflow.modules.admin.dto.AdminOperationLogResponse;
import com.vernflow.modules.admin.dto.AdminParseTaskSummaryResponse;
import com.vernflow.modules.admin.dto.AdminRecentActivityResponse;
import com.vernflow.modules.admin.dto.AdminSessionSummaryResponse;
import com.vernflow.modules.admin.dto.AdminStorageCreateRequest;
import com.vernflow.modules.admin.dto.AdminStorageDeleteRequest;
import com.vernflow.modules.admin.dto.AdminStorageEntryResponse;
import com.vernflow.modules.admin.dto.AdminStorageListResponse;
import com.vernflow.modules.admin.dto.AdminSystemOverviewResponse;
import com.vernflow.modules.admin.dto.AdminUserSummaryResponse;
import com.vernflow.modules.admin.dto.AdminWorkbenchRunSummaryResponse;
import com.vernflow.modules.admin.repository.AdminOperationLogRepository;
import com.vernflow.modules.assistant.domain.AssistantSession;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.assistant.repository.AssistantSessionRepository;
import com.vernflow.modules.assistant.service.WorkspaceAssistantService;
import com.vernflow.modules.paper.domain.ParseTask;
import com.vernflow.modules.paper.repository.ParseTaskRepository;
import com.vernflow.modules.user.service.UserAuthService;
import com.vernflow.modules.workbench.domain.WorkbenchRun;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;
import com.vernflow.modules.workbench.repository.WorkbenchRunRepository;
import com.vernflow.modules.workbench.service.WorkbenchRunService;
import com.vernflow.modules.workspace.service.UserWorkspaceRootService;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class AdminConsoleService {

    private final StorageProperties storageProperties;
    private final AdminProperties adminProperties;
    private final AiServiceProperties aiServiceProperties;
    private final MultipartProperties multipartProperties;
    private final UserAuthService userAuthService;
    private final UserWorkspaceRootService userWorkspaceRootService;
    private final AssistantSessionRepository assistantSessionRepository;
    private final WorkspaceAssistantService workspaceAssistantService;
    private final WorkbenchRunRepository workbenchRunRepository;
    private final WorkbenchRunService workbenchRunService;
    private final ParseTaskRepository parseTaskRepository;
    private final AdminOperationLogRepository adminOperationLogRepository;
    private final AdminOperationLogService adminOperationLogService;
    private final ObjectMapper objectMapper;

    public AdminConsoleService(
            StorageProperties storageProperties,
            AdminProperties adminProperties,
            AiServiceProperties aiServiceProperties,
            MultipartProperties multipartProperties,
            UserAuthService userAuthService,
            UserWorkspaceRootService userWorkspaceRootService,
            AssistantSessionRepository assistantSessionRepository,
            WorkspaceAssistantService workspaceAssistantService,
            WorkbenchRunRepository workbenchRunRepository,
            WorkbenchRunService workbenchRunService,
            ParseTaskRepository parseTaskRepository,
            AdminOperationLogRepository adminOperationLogRepository,
            AdminOperationLogService adminOperationLogService,
            ObjectMapper objectMapper
    ) {
        this.storageProperties = storageProperties;
        this.adminProperties = adminProperties;
        this.aiServiceProperties = aiServiceProperties;
        this.multipartProperties = multipartProperties;
        this.userAuthService = userAuthService;
        this.userWorkspaceRootService = userWorkspaceRootService;
        this.assistantSessionRepository = assistantSessionRepository;
        this.workspaceAssistantService = workspaceAssistantService;
        this.workbenchRunRepository = workbenchRunRepository;
        this.workbenchRunService = workbenchRunService;
        this.parseTaskRepository = parseTaskRepository;
        this.adminOperationLogRepository = adminOperationLogRepository;
        this.adminOperationLogService = adminOperationLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse loadDashboard() {
        StorageMetrics storageMetrics = collectStorageMetrics();
        List<AdminRecentActivityResponse> recentActivities = new ArrayList<>();

        assistantSessionRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 3))
                .forEach(session -> recentActivities.add(new AdminRecentActivityResponse(
                        "assistant",
                        session.getTitle(),
                        session.getSummary(),
                        session.getUpdatedAt()
                )));
        workbenchRunRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 3))
                .forEach(run -> recentActivities.add(new AdminRecentActivityResponse(
                        "workbench",
                        run.getName(),
                        run.getStatus().name().toLowerCase(Locale.ROOT),
                        run.getCreatedAt()
                )));
        adminOperationLogRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .limit(4)
                .forEach(log -> recentActivities.add(new AdminRecentActivityResponse(
                        "audit",
                        log.getAction(),
                        StringUtils.hasText(log.getTargetPath()) ? log.getTargetPath() : log.getDetail(),
                        log.getCreatedAt()
                )));

        List<AdminRecentActivityResponse> sortedActivities = recentActivities.stream()
                .sorted(Comparator.comparing(AdminRecentActivityResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(8)
                .toList();

        return new AdminDashboardResponse(
                storageMetrics.fileCount(),
                storageMetrics.folderCount(),
                assistantSessionRepository.count(),
                workbenchRunRepository.count(),
                parseTaskRepository.count(),
                adminOperationLogRepository.count(),
                sortedActivities
        );
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listUsers() {
        return userAuthService.listUsers().stream()
                .map(this::toUserSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserSummaryResponse getUser(Long userId) {
        return toUserSummary(userAuthService.requireUser(userId).getId());
    }

    @Transactional(readOnly = true)
    public AdminStorageListResponse listStorage(String rawPath) {
        String currentPath = normalizeAdminPath(rawPath);
        Path directory = ensureWorkspaceDirectory(resolvePath(currentPath));

        try (Stream<Path> children = Files.list(directory)) {
            List<AdminStorageEntryResponse> items = children
                    .sorted(entryComparator())
                    .map(this::toStorageEntry)
                    .toList();
            return new AdminStorageListResponse(currentPath, parentPathOf(currentPath), items);
        } catch (IOException exception) {
            throw new BusinessException("Failed to read admin storage directory: " + exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AdminStorageListResponse listUserStorage(Long userId, String rawPath) {
        String currentPath = normalizeAdminPath(rawPath);
        Path directory = ensureWorkspaceDirectory(resolveUserPath(userId, currentPath));

        try (Stream<Path> children = Files.list(directory)) {
            List<AdminStorageEntryResponse> items = children
                    .sorted(entryComparator())
                    .map(path -> toStorageEntry(path, userWorkspaceRoot(userId)))
                    .toList();
            return new AdminStorageListResponse(currentPath, parentPathOf(currentPath), items);
        } catch (IOException exception) {
            throw new BusinessException("Failed to read user storage directory: " + exception.getMessage());
        }
    }

    public AdminStorageListResponse createFolder(AdminStorageCreateRequest request, String actor) {
        String currentPath = normalizeAdminPath(request.parentPath());
        Path parent = ensureWorkspaceDirectory(resolvePath(currentPath));
        String folderName = normalizeLeafName(request.name());
        Path target = parent.resolve(folderName).normalize().toAbsolutePath();
        ensureWithinWorkspaceRoot(target);

        try {
            Files.createDirectories(target);
        } catch (IOException exception) {
            throw new BusinessException("Failed to create admin folder: " + exception.getMessage());
        }

        adminOperationLogService.log(actor, "CREATE_FOLDER", "workspace", toAdminPath(target), "Created internal storage folder");
        return listStorage(currentPath);
    }

    public AdminStorageListResponse createUserFolder(Long userId, AdminStorageCreateRequest request, String actor) {
        String currentPath = normalizeAdminPath(request.parentPath());
        Path parent = ensureWorkspaceDirectory(resolveUserPath(userId, currentPath));
        String folderName = normalizeLeafName(request.name());
        Path target = parent.resolve(folderName).normalize().toAbsolutePath();
        ensureWithinWorkspaceRoot(target, userWorkspaceRoot(userId));

        try {
            Files.createDirectories(target);
        } catch (IOException exception) {
            throw new BusinessException("Failed to create user folder: " + exception.getMessage());
        }

        adminOperationLogService.log(actor, "CREATE_USER_FOLDER", "workspace-user", userScopedTarget(userId, toAdminPath(target, userWorkspaceRoot(userId))), "Created user workspace folder");
        return listUserStorage(userId, currentPath);
    }

    public AdminStorageListResponse deleteStorage(AdminStorageDeleteRequest request, String actor) {
        if (request.paths() == null || request.paths().isEmpty()) {
            throw new BusinessException("Please select at least one storage item.");
        }

        String returnPath = "/";
        for (String rawPath : request.paths()) {
            String normalizedPath = normalizeAdminPath(rawPath);
            Path target = resolvePath(normalizedPath);
            if (target.equals(workspaceRoot())) {
                throw new BusinessException("The workspace root cannot be deleted.");
            }
            returnPath = parentPathOf(normalizedPath);
            deleteRecursively(target);
            adminOperationLogService.log(actor, "DELETE_STORAGE", "workspace", normalizedPath, "Deleted internal storage entry");
        }

        return listStorage(returnPath);
    }

    public AdminStorageListResponse deleteUserStorage(Long userId, AdminStorageDeleteRequest request, String actor) {
        if (request.paths() == null || request.paths().isEmpty()) {
            throw new BusinessException("Please select at least one storage item.");
        }

        String returnPath = "/";
        Path root = userWorkspaceRoot(userId);
        for (String rawPath : request.paths()) {
            String normalizedPath = normalizeAdminPath(rawPath);
            Path target = resolveUserPath(userId, normalizedPath);
            if (target.equals(root)) {
                throw new BusinessException("The user workspace root cannot be deleted.");
            }
            returnPath = parentPathOf(normalizedPath);
            deleteRecursively(target);
            adminOperationLogService.log(actor, "DELETE_USER_STORAGE", "workspace-user", userScopedTarget(userId, normalizedPath), "Deleted user workspace entry");
        }

        return listUserStorage(userId, returnPath);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSessionSummaryResponse> listAssistantSessions(int page, int size) {
        return PageResponse.from(
                assistantSessionRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toSessionSummary)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSessionSummaryResponse> listAssistantSessions(Long userId, int page, int size) {
        return PageResponse.from(
                assistantSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toSessionSummary)
        );
    }

    @Transactional(readOnly = true)
    public AssistantSessionDetailResponse getAssistantSession(Long sessionId) {
        return workspaceAssistantService.getSession(sessionId);
    }

    @Transactional(readOnly = true)
    public AssistantSessionDetailResponse getAssistantSession(Long userId, Long sessionId) {
        return workspaceAssistantService.getSession(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWorkbenchRunSummaryResponse> listWorkbenchRuns(int page, int size) {
        return PageResponse.from(
                workbenchRunRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toWorkbenchRunSummary)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWorkbenchRunSummaryResponse> listWorkbenchRuns(Long userId, int page, int size) {
        return PageResponse.from(
                workbenchRunRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toWorkbenchRunSummary)
        );
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse getWorkbenchRun(Long runId) {
        return workbenchRunService.loadRunDetail(runId);
    }

    @Transactional(readOnly = true)
    public WorkbenchRunDetailResponse getWorkbenchRun(Long userId, Long runId) {
        return workbenchRunService.loadRunDetail(userId, runId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminParseTaskSummaryResponse> listParseTasks(int page, int size) {
        return PageResponse.from(
                parseTaskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toParseTaskSummary)
        );
    }

    @Transactional(readOnly = true)
    public AdminSystemOverviewResponse loadSystemOverview() {
        return new AdminSystemOverviewResponse(
                adminProperties.username(),
                workspaceRoot().toString(),
                storageProperties.paperUploadDir().normalize().toAbsolutePath().toString(),
                aiServiceProperties.baseUrl().toString(),
                aiServiceProperties.timeout().toSeconds(),
                multipartProperties.getMaxFileSize().toString(),
                multipartProperties.getMaxRequestSize().toString()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOperationLogResponse> listOperationLogs(int page, int size) {
        return PageResponse.from(
                adminOperationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(normalizePageIndex(page), normalizePageSize(size)))
                        .map(this::toOperationLogResponse)
        );
    }

    private AdminSessionSummaryResponse toSessionSummary(AssistantSession session) {
        return new AdminSessionSummaryResponse(
                session.getId(),
                session.getTitle(),
                StringUtils.hasText(session.getSummary()) ? session.getSummary() : "",
                countJsonArrayItems(session.getMessagesJson()),
                session.isGlobalScope(),
                countJsonArrayItems(session.getWorkingFilesJson()),
                session.getUpdatedAt()
        );
    }

    private AdminWorkbenchRunSummaryResponse toWorkbenchRunSummary(WorkbenchRun run) {
        return new AdminWorkbenchRunSummaryResponse(
                run.getId(),
                run.getName(),
                run.getStatus().name(),
                run.getInspectMode(),
                run.isGlobalScope(),
                countJsonArrayItems(run.getWorkingFilesJson()),
                run.getCreatedAt()
        );
    }

    private AdminParseTaskSummaryResponse toParseTaskSummary(ParseTask task) {
        return new AdminParseTaskSummaryResponse(
                task.getId(),
                task.getPaper() == null ? null : task.getPaper().getId(),
                task.getPaper() == null ? null : task.getPaper().getTitle(),
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getQueueOrder(),
                task.getUpdatedAt()
        );
    }

    private AdminOperationLogResponse toOperationLogResponse(AdminOperationLog log) {
        return new AdminOperationLogResponse(
                log.getId(),
                log.getActor(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetPath(),
                log.getDetail(),
                log.getCreatedAt()
        );
    }

    private AdminUserSummaryResponse toUserSummary(UserAuthService.UserSummary summary) {
        return toUserSummary(summary.id(), summary.username(), summary.displayName());
    }

    private AdminUserSummaryResponse toUserSummary(Long userId) {
        var user = userAuthService.requireUser(userId);
        return toUserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }

    private AdminUserSummaryResponse toUserSummary(Long userId, String username, String displayName) {
        StorageMetrics storageMetrics = collectStorageMetrics(userWorkspaceRoot(userId));
        return new AdminUserSummaryResponse(
                userId,
                username,
                displayName,
                storageMetrics.fileCount(),
                storageMetrics.folderCount(),
                assistantSessionRepository.countByUserId(userId),
                workbenchRunRepository.countByUserId(userId)
        );
    }

    private int countJsonArrayItems(String json) {
        if (!StringUtils.hasText(json)) {
            return 0;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray() ? node.size() : 0;
        } catch (IOException exception) {
            return 0;
        }
    }

    private StorageMetrics collectStorageMetrics() {
        return collectStorageMetrics(workspaceRoot());
    }

    private StorageMetrics collectStorageMetrics(Path rootPath) {
        Path root = ensureWorkspaceDirectory(rootPath);
        try (Stream<Path> paths = Files.walk(root)) {
            long[] counts = paths.reduce(
                    new long[]{0L, 0L},
                    (accumulator, path) -> {
                        if (!path.equals(root)) {
                            if (Files.isDirectory(path)) {
                                accumulator[1]++;
                            } else {
                                accumulator[0]++;
                            }
                        }
                        return accumulator;
                    },
                    (left, right) -> new long[]{left[0] + right[0], left[1] + right[1]}
            );
            return new StorageMetrics(counts[0], counts[1]);
        } catch (IOException exception) {
            throw new BusinessException("Failed to scan workspace storage: " + exception.getMessage());
        }
    }

    private AdminStorageEntryResponse toStorageEntry(Path realPath) {
        return toStorageEntry(realPath, workspaceRoot());
    }

    private AdminStorageEntryResponse toStorageEntry(Path realPath, Path rootPath) {
        boolean directory = Files.isDirectory(realPath);
        return new AdminStorageEntryResponse(
                realPath.getFileName().toString(),
                toAdminPath(realPath, rootPath),
                directory ? "folder" : "file",
                directory ? null : fileSize(realPath),
                lastModified(realPath)
        );
    }

    private String normalizeAdminPath(String rawPath) {
        if (!StringUtils.hasText(rawPath) || "/".equals(rawPath.trim())) {
            return "/";
        }

        String normalized = rawPath.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String parentPathOf(String adminPath) {
        if (!StringUtils.hasText(adminPath) || "/".equals(adminPath)) {
            return "/";
        }

        int lastSlash = adminPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return adminPath.substring(0, lastSlash);
    }

    private Path resolvePath(String adminPath) {
        String normalized = normalizeAdminPath(adminPath);
        String relative = "/".equals(normalized) ? "" : normalized.substring(1);
        Path resolved = workspaceRoot()
                .resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize()
                .toAbsolutePath();
        ensureWithinWorkspaceRoot(resolved);
        return resolved;
    }

    private Path resolveUserPath(Long userId, String adminPath) {
        String normalized = normalizeAdminPath(adminPath);
        String relative = "/".equals(normalized) ? "" : normalized.substring(1);
        Path root = userWorkspaceRoot(userId);
        Path resolved = root
                .resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize()
                .toAbsolutePath();
        ensureWithinWorkspaceRoot(resolved, root);
        return resolved;
    }

    private String toAdminPath(Path realPath) {
        return toAdminPath(realPath, workspaceRoot());
    }

    private String toAdminPath(Path realPath, Path rootPath) {
        Path relative = rootPath.relativize(realPath.normalize().toAbsolutePath());
        String text = relative.toString().replace('\\', '/');
        return text.isBlank() ? "/" : "/" + text;
    }

    private Path workspaceRoot() {
        return storageProperties.workspaceRootDir().normalize().toAbsolutePath();
    }

    private Path userWorkspaceRoot(Long userId) {
        return userWorkspaceRootService.resolveUserWorkspaceRoot(userId);
    }

    private Path ensureWorkspaceDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new BusinessException("Failed to initialize workspace storage: " + exception.getMessage());
        }
    }

    private void ensureWithinWorkspaceRoot(Path path) {
        ensureWithinWorkspaceRoot(path, workspaceRoot());
    }

    private void ensureWithinWorkspaceRoot(Path path, Path rootPath) {
        if (!path.startsWith(rootPath)) {
            throw new BusinessException("Admin storage path escapes the workspace root.");
        }
    }

    private String userScopedTarget(Long userId, String adminPath) {
        return "user:" + userId + ":" + adminPath;
    }

    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException runtimeException) {
            Throwable cause = runtimeException.getCause();
            if (cause instanceof IOException ioException) {
                throw new BusinessException("Failed to delete storage item: " + ioException.getMessage());
            }
            throw runtimeException;
        } catch (IOException exception) {
            throw new BusinessException("Failed to delete storage item: " + exception.getMessage());
        }
    }

    private Comparator<Path> entryComparator() {
        return Comparator
                .comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    private String normalizeLeafName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException("Name cannot be empty.");
        }

        String normalized = Path.of(rawName).getFileName().toString().trim();
        if (!StringUtils.hasText(normalized) || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException("Illegal name: " + rawName);
        }
        return normalized;
    }

    private Long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long lastModified(Path filePath) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(filePath);
            return lastModified.toMillis();
        } catch (IOException exception) {
            return null;
        }
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private int normalizePageIndex(int page) {
        return Math.max(page, 0);
    }

    private record StorageMetrics(long fileCount, long folderCount) {
    }
}
