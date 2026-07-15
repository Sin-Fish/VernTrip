package com.vernflow.modules.workspace.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.assistant.dto.AssistantMessagePayload;
import com.vernflow.modules.assistant.dto.AssistantSessionDetailResponse;
import com.vernflow.modules.assistant.dto.AssistantSessionSummaryResponse;
import com.vernflow.modules.assistant.service.WorkspaceAssistantService;
import com.vernflow.modules.workbench.service.WorkbenchRunService;
import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.dto.WorkspaceStateSnapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Builds the frontend bootstrap payload around the generic workspace shell.
 */
@Service
public class WorkspaceBootstrapService {

    private static final String ROOT_PATH = "workspace://";
    private static final String DEFAULT_SELECTED_NODE = "branch";

    private final UserWorkspaceRootService userWorkspaceRootService;
    private final WorkspaceStateService workspaceStateService;
    private final WorkbenchRunService workbenchRunService;
    private final WorkspaceAssistantService workspaceAssistantService;

    public WorkspaceBootstrapService(
            UserWorkspaceRootService userWorkspaceRootService,
            WorkspaceStateService workspaceStateService,
            WorkbenchRunService workbenchRunService,
            WorkspaceAssistantService workspaceAssistantService
    ) {
        this.userWorkspaceRootService = userWorkspaceRootService;
        this.workspaceStateService = workspaceStateService;
        this.workbenchRunService = workbenchRunService;
        this.workspaceAssistantService = workspaceAssistantService;
    }

    /**
     * Build the initial workspace payload.
     *
     * @return bootstrap payload for the frontend shell
     */
    @Transactional(readOnly = true)
    public WorkspaceBootstrapResponse loadBootstrap(Long userId) {
        Path workspaceRoot = ensureWorkspaceRoot(userWorkspaceRootService.resolveUserWorkspaceRoot(userId));
        boolean hasPersistedState = workspaceStateService.hasPersistedState(userId);
        WorkspaceStateSnapshot persistedState = workspaceStateService.loadSnapshot(userId);

        WorkspaceCounts counts = countWorkspaceEntries(workspaceRoot);
        List<WorkspaceItemRef> treeItems = listRootEntries(workspaceRoot);
        List<WorkspaceItemRef> workingFiles = normalizeWorkingFiles(persistedState.workingFiles(), workspaceRoot);
        if (!hasPersistedState && workingFiles.isEmpty()) {
            workingFiles = suggestInitialWorkingFiles(workspaceRoot);
        }

        String selectedTreePath = resolveSelectedTreePath(
                persistedState.selectedTreePath(),
                treeItems,
                workingFiles
        );
        List<AssistantSessionSummaryResponse> assistantSessionSummaries = workspaceAssistantService.listSessions(userId);
        List<WorkspaceBootstrapResponse.AssistantSessionCard> assistantSessions = assistantSessionSummaries.stream()
                .map(session -> new WorkspaceBootstrapResponse.AssistantSessionCard(
                        Long.toString(session.id()),
                        session.title(),
                        session.summary()
                ))
                .toList();
        AssistantSessionDetailResponse selectedAssistantSession = assistantSessionSummaries.isEmpty()
                ? null
                : workspaceAssistantService.getSession(userId, assistantSessionSummaries.getFirst().id());
        String selectedSessionId = selectedAssistantSession == null ? null : Long.toString(selectedAssistantSession.id());
        String scopeMode = persistedState.globalScope() ? "global_workspace" : "working_files";

        return new WorkspaceBootstrapResponse(
                new WorkspaceBootstrapResponse.HomeSummary(
                        counts.fileCount(),
                        counts.folderCount(),
                        workingFiles.size(),
                        scopeMode
                ),
                defaultPageMeta(),
                treeItems,
                workingFiles,
                persistedState.globalScope(),
                selectedTreePath,
                DEFAULT_SELECTED_NODE,
                selectedSessionId,
                persistedState.homeTaskDraft(),
                persistedState.workbenchPrompt(),
                buildLibraryCards(treeItems),
                workbenchRunService.listWorkbenchRecords(userId),
                defaultWorkbenchNodes(),
                defaultGraphLinks(),
                List.of("scope", "retriever", "branch", "writer", "audit"),
                buildWorkbenchWorkspaceStats(counts, workingFiles.size(), scopeMode),
                assistantSessions,
                selectedAssistantSession == null ? List.of() : toBootstrapAssistantMessages(selectedAssistantSession.messages()),
                defaultSettingsTabs(),
                defaultSkillLibraryRows(),
                defaultSkillPermissions()
        );
    }

    private Map<String, WorkspaceBootstrapResponse.PageMeta> defaultPageMeta() {
        Map<String, WorkspaceBootstrapResponse.PageMeta> meta = new LinkedHashMap<>();
        meta.put("home", new WorkspaceBootstrapResponse.PageMeta("Home", "Import files, run tasks, and inspect results from one workspace shell."));
        meta.put("library", new WorkspaceBootstrapResponse.PageMeta("Library", "Browse, upload, rename, move, and organize workspace files."));
        meta.put("workbench", new WorkspaceBootstrapResponse.PageMeta("Workbench", "Review task history and open persisted execution graphs."));
        meta.put("assistant", new WorkspaceBootstrapResponse.PageMeta("Assistant", "Ask questions inside the current workspace scope and inspect cited evidence."));
        meta.put("settings", new WorkspaceBootstrapResponse.PageMeta("Settings", "Manage agent behavior, skill permissions, and retrieval defaults."));
        return meta;
    }

    private List<WorkspaceItemRef> listRootEntries(Path workspaceRoot) {
        try (Stream<Path> children = Files.list(workspaceRoot)) {
            return children
                    .sorted(pathComparator())
                    .map(path -> toItemRef(path, workspaceRoot))
                    .toList();
        } catch (IOException exception) {
            throw new BusinessException("Failed to read workspace root: " + exception.getMessage());
        }
    }

    private WorkspaceCounts countWorkspaceEntries(Path workspaceRoot) {
        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            List<Path> items = walk
                    .filter(path -> !path.equals(workspaceRoot))
                    .toList();
            long folderCount = items.stream().filter(Files::isDirectory).count();
            long fileCount = items.size() - folderCount;
            return new WorkspaceCounts(fileCount, folderCount);
        } catch (IOException exception) {
            throw new BusinessException("Failed to inspect workspace files: " + exception.getMessage());
        }
    }

    private List<WorkspaceItemRef> normalizeWorkingFiles(List<WorkspaceItemRef> items, Path workspaceRoot) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .map(item -> resolveExistingItem(item, workspaceRoot))
                .filter(item -> item != null)
                .distinct()
                .toList();
    }

    private WorkspaceItemRef resolveExistingItem(WorkspaceItemRef item, Path workspaceRoot) {
        if (item == null || item.path() == null || item.path().isBlank()) {
            return null;
        }

        Path realPath = resolveVirtualPath(item.path(), workspaceRoot);
        if (realPath == null || !Files.exists(realPath)) {
            return null;
        }

        return toItemRef(realPath, workspaceRoot);
    }

    private List<WorkspaceItemRef> suggestInitialWorkingFiles(Path workspaceRoot) {
        try (Stream<Path> walk = Files.walk(workspaceRoot, 8)) {
            return walk
                    .filter(path -> !path.equals(workspaceRoot))
                    .filter(Files::isRegularFile)
                    .sorted(pathComparator())
                    .limit(2)
                    .map(path -> toItemRef(path, workspaceRoot))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String resolveSelectedTreePath(
            String selectedTreePath,
            List<WorkspaceItemRef> treeItems,
            List<WorkspaceItemRef> workingFiles
    ) {
        if (selectedTreePath != null && treeItems.stream().anyMatch(item -> selectedTreePath.equals(item.path()))) {
            return selectedTreePath;
        }
        if (!treeItems.isEmpty()) {
            return treeItems.getFirst().path();
        }
        if (!workingFiles.isEmpty()) {
            return workingFiles.getFirst().path();
        }
        return ROOT_PATH;
    }

    private List<WorkspaceBootstrapResponse.LibraryItem> buildLibraryCards(List<WorkspaceItemRef> treeItems) {
        return treeItems.stream()
                .limit(6)
                .map(item -> new WorkspaceBootstrapResponse.LibraryItem(
                        item.label(),
                        item.kind(),
                        extensionOf(item.label()),
                        item.kind().equals("folder")
                                ? "Workspace folder ready for curation and follow-up actions."
                                : "Workspace file ready to be added to the active task scope.",
                        item.path()
                ))
                .toList();
    }

    private List<WorkspaceBootstrapResponse.WorkbenchNode> defaultWorkbenchNodes() {
        return List.of(
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "scope",
                        "scope",
                        "Read working files",
                        "done",
                        "done",
                        "working files",
                        "n1",
                        "completed",
                        "done",
                        "passed",
                        "done",
                        false,
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Scope is defined", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Workspace references are readable", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "Expand to full workspace if needed", "review")
                        )
                ),
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "retriever",
                        "retriever",
                        "Local retrieval",
                        "done",
                        "done",
                        "top 24",
                        "n2",
                        "completed",
                        "done",
                        "running",
                        "run",
                        false,
                        true,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Candidate evidence remains traceable", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Hits cover the active topic", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "Edge references may still be missing", "review")
                        )
                ),
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "branch",
                        "branch",
                        "Web branch",
                        "running",
                        "run",
                        "fallback",
                        "branch",
                        "running",
                        "run",
                        "pending",
                        "wait",
                        true,
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Fallback branch is enabled", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Branch can merge back into main path", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "Source reliability still needs inspection", "review")
                        )
                ),
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "selector",
                        "selector",
                        "Coverage selector",
                        "bypassed",
                        "wait",
                        "local path",
                        "n3",
                        "pending",
                        "wait",
                        "pending",
                        "wait",
                        false,
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Candidate evidence is preserved", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Coverage strategy is readable", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "MMR strategy can be swapped later", "review")
                        )
                ),
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "writer",
                        "writer",
                        "Organize materials",
                        "running",
                        "run",
                        "workspace",
                        "n4",
                        "pending",
                        "wait",
                        "pending",
                        "wait",
                        false,
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Output remains bound to workspace files", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Organization steps can be applied", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "Directory changes may affect older references", "review")
                        )
                ),
                new WorkspaceBootstrapResponse.WorkbenchNode(
                        "audit",
                        "audit",
                        "Inspection",
                        "queued",
                        "wait",
                        "focus",
                        "n5",
                        "pending",
                        "wait",
                        "pending",
                        "wait",
                        false,
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Inspection chain is connected", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("on", "Claim checks are configured", "ok"),
                                new WorkspaceBootstrapResponse.NodeCheck("warn", "External verification results can be expanded", "review")
                        )
                )
        );
    }

    private List<WorkspaceBootstrapResponse.WorkbenchLink> defaultGraphLinks() {
        return List.of(
                new WorkspaceBootstrapResponse.WorkbenchLink("scope", "retriever", false),
                new WorkspaceBootstrapResponse.WorkbenchLink("retriever", "selector", true),
                new WorkspaceBootstrapResponse.WorkbenchLink("retriever", "branch", true),
                new WorkspaceBootstrapResponse.WorkbenchLink("branch", "writer", false),
                new WorkspaceBootstrapResponse.WorkbenchLink("selector", "writer", true),
                new WorkspaceBootstrapResponse.WorkbenchLink("writer", "audit", false)
        );
    }

    private List<WorkspaceBootstrapResponse.KeyValueStat> buildWorkbenchWorkspaceStats(
            WorkspaceCounts counts,
            int workingFileCount,
            String scopeMode
    ) {
        return List.of(
                new WorkspaceBootstrapResponse.KeyValueStat("Folders", Long.toString(counts.folderCount())),
                new WorkspaceBootstrapResponse.KeyValueStat("Files", Long.toString(counts.fileCount())),
                new WorkspaceBootstrapResponse.KeyValueStat("Working files", Integer.toString(workingFileCount)),
                new WorkspaceBootstrapResponse.KeyValueStat(
                        "Scope",
                        "global_workspace".equals(scopeMode) ? "Global workspace" : "Working files"
                )
        );
    }

    private List<WorkspaceBootstrapResponse.AssistantMessage> toBootstrapAssistantMessages(
            List<AssistantMessagePayload> messages
    ) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        return messages.stream()
                .map(message -> new WorkspaceBootstrapResponse.AssistantMessage(
                        message.id(),
                        message.role(),
                        message.text(),
                        message.cites() == null ? List.of() : message.cites()
                ))
                .toList();
    }

    private List<WorkspaceBootstrapResponse.SettingsTab> defaultSettingsTabs() {
        return List.of(
                new WorkspaceBootstrapResponse.SettingsTab("basic", "Basic"),
                new WorkspaceBootstrapResponse.SettingsTab("agent", "Agent"),
                new WorkspaceBootstrapResponse.SettingsTab("skills", "Skills"),
                new WorkspaceBootstrapResponse.SettingsTab("advanced", "Advanced")
        );
    }

    private List<WorkspaceBootstrapResponse.SkillLibraryRow> defaultSkillLibraryRows() {
        return List.of(
                new WorkspaceBootstrapResponse.SkillLibraryRow(
                        "Evidence-first writing",
                        "Retrieve local evidence, draft the answer, and keep inspection ready.",
                        List.of("Details", "Clone", "Disable")
                ),
                new WorkspaceBootstrapResponse.SkillLibraryRow(
                        "Workspace organizer",
                        "Suggest renames, moves, and folder groupings from current files.",
                        List.of("Details", "Clone", "Disable")
                ),
                new WorkspaceBootstrapResponse.SkillLibraryRow(
                        "Web fill-in",
                        "Read public pages and capture external context back into the workspace.",
                        List.of("Details", "Clone", "Disable")
                ),
                new WorkspaceBootstrapResponse.SkillLibraryRow(
                        "Project document export",
                        "Export structured workspace outputs into docx or markdown deliverables.",
                        List.of("Edit", "Clone", "Delete")
                )
        );
    }

    private List<WorkspaceBootstrapResponse.SkillPermission> defaultSkillPermissions() {
        return List.of(
                new WorkspaceBootstrapResponse.SkillPermission(
                        "evidence-doc",
                        "Evidence-first writing",
                        "System skill - enabled",
                        true,
                        List.of(
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("decision", "Decision", "Plan the path for the current task.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("execution", "Execution", "Run retrieval and draft generation.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("inspection", "Inspection", "Check the resulting claims and evidence.", true)
                        )
                ),
                new WorkspaceBootstrapResponse.SkillPermission(
                        "organize",
                        "Workspace organizer",
                        "System skill - enabled",
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("decision", "Decision", "Generate organization guidance.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("execution", "Execution", "Apply file operations safely.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("inspection", "Inspection", "Leave organizer runs unchecked by default.", false)
                        )
                ),
                new WorkspaceBootstrapResponse.SkillPermission(
                        "web-fill",
                        "Web fill-in",
                        "System skill - branch capability",
                        false,
                        List.of(
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("decision", "Decision", "Allow this as a fallback branch.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("execution", "Execution", "Read public pages when needed.", true),
                                new WorkspaceBootstrapResponse.SkillPermissionAgent("inspection", "Inspection", "Do not run separately by default.", false)
                        )
                )
        );
    }

    private WorkspaceItemRef toItemRef(Path path, Path workspaceRoot) {
        boolean directory = Files.isDirectory(path);
        return new WorkspaceItemRef(
                toVirtualPath(path, workspaceRoot),
                path.getFileName().toString(),
                directory ? "folder" : "file"
        );
    }

    private String toVirtualPath(Path path, Path workspaceRoot) {
        Path relative = workspaceRoot.relativize(path.normalize().toAbsolutePath());
        String text = relative.toString().replace('\\', '/');
        return text.isBlank() ? ROOT_PATH : ROOT_PATH + text;
    }

    private Path resolveVirtualPath(String virtualPath, Path workspaceRoot) {
        if (virtualPath == null || virtualPath.isBlank() || ROOT_PATH.equals(virtualPath)) {
            return workspaceRoot;
        }
        if (!virtualPath.startsWith(ROOT_PATH)) {
            return null;
        }

        String relative = virtualPath.substring(ROOT_PATH.length());
        Path resolved = workspaceRoot.resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize()
                .toAbsolutePath();
        return resolved.startsWith(workspaceRoot) ? resolved : null;
    }

    private Path ensureWorkspaceRoot(Path workspaceRoot) {
        try {
            Files.createDirectories(workspaceRoot);
            return workspaceRoot;
        } catch (IOException exception) {
            throw new BusinessException("Failed to initialize workspace root: " + exception.getMessage());
        }
    }

    private Comparator<Path> pathComparator() {
        return Comparator
                .comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    private String extensionOf(String label) {
        int dotIndex = label.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == label.length() - 1) {
            return "";
        }
        return label.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private record WorkspaceCounts(long fileCount, long folderCount) {
    }
}
