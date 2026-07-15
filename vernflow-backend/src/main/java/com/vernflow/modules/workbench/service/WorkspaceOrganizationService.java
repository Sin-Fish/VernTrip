package com.vernflow.modules.workbench.service;

import com.vernflow.modules.workbench.model.WorkspaceOrganizationPlan;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.service.WorkspaceFinderService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds and executes deterministic workspace-organization plans for the workbench demo flow.
 */
@Service
public class WorkspaceOrganizationService {

    private static final String WORKSPACE_ROOT = "workspace://";
    private static final String ORGANIZER_ROOT_NAME = "资料整理";
    private static final String REPORT_ROOT_NAME = "执行报告";

    private static final Set<String> PAPER_EXTENSIONS = Set.of("pdf");
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("doc", "docx", "ppt", "pptx", "xls", "xlsx");
    private static final Set<String> NOTE_EXTENSIONS = Set.of("md", "txt");
    private static final Set<String> DATA_EXTENSIONS = Set.of("csv", "tsv", "json", "yaml", "yml", "xml");
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "gradle", "properties", "js", "jsx", "ts", "tsx", "vue", "go", "rs", "cpp", "c",
            "cs", "sql", "sh"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp");

    private final WorkspaceFinderService workspaceFinderService;

    public WorkspaceOrganizationService(WorkspaceFinderService workspaceFinderService) {
        this.workspaceFinderService = workspaceFinderService;
    }

    public WorkspaceOrganizationPlan plan(
            Long userId,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            boolean generateExecutionReport
    ) {
        List<WorkspaceItemRef> organizationScope = resolveOrganizationScope(globalScope, workingFiles);
        List<WorkspaceItemRef> scopeFiles = workspaceFinderService.collectFiles(userId, organizationScope);

        String baseDirectoryPath = resolvePlanBaseDirectory(scopeFiles, organizationScope);
        String organizerRootPath = resolveManagedRootPath(baseDirectoryPath, ORGANIZER_ROOT_NAME);
        String reportDirectoryPath = resolveManagedRootPath(baseDirectoryPath, REPORT_ROOT_NAME);
        boolean organizerRootExplicitlySelected = scopeExplicitlyTargets(organizationScope, organizerRootPath);
        boolean reportRootExplicitlySelected = scopeExplicitlyTargets(organizationScope, reportDirectoryPath);
        OrganizationCandidates candidates = selectOrganizationCandidates(
                scopeFiles,
                organizerRootPath,
                organizerRootExplicitlySelected,
                reportDirectoryPath,
                reportRootExplicitlySelected
        );
        List<WorkspaceItemRef> selectedFiles = candidates.selectedFiles();
        String reportFilePath = generateExecutionReport
                ? joinPath(reportDirectoryPath, buildReportFileName(workingFiles))
                : null;

        if (selectedFiles.isEmpty()) {
            return new WorkspaceOrganizationPlan(
                    baseDirectoryPath,
                    organizerRootPath,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    generateExecutionReport,
                    reportDirectoryPath,
                    reportFilePath,
                    List.of("当前没有可整理的文件，请先把资料加入工作文件。")
            );
        }

        List<String> scopeRoots = organizationScope.stream()
                .map(item -> isFolder(item) ? item.path() : parentDirectoryOf(item.path()))
                .filter(StringUtils::hasText)
                .map(this::normalizePath)
                .distinct()
                .toList();

        Map<String, List<WorkspaceItemRef>> categorized = new LinkedHashMap<>();
        for (WorkspaceItemRef file : selectedFiles) {
            categorized.computeIfAbsent(categoryFor(file), ignored -> new ArrayList<>()).add(file);
        }

        List<WorkspaceOrganizationPlan.CategoryFolder> folders = categorized.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new WorkspaceOrganizationPlan.CategoryFolder(
                        entry.getKey(),
                        joinPath(organizerRootPath, entry.getKey()),
                        entry.getValue().size()
                ))
                .toList();

        List<WorkspaceOrganizationPlan.MoveOperation> moves = buildMovePlan(folders, categorized);
        List<String> cleanupCandidates = selectedFiles.stream()
                .map(this::parentDirectoryOf)
                .map(this::normalizePath)
                .filter(path -> !samePath(path, organizerRootPath))
                .filter(path -> !samePath(path, reportDirectoryPath))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .distinct()
                .toList();

        List<String> warnings = new ArrayList<>();
        String defaultOrganizerRoot = joinPath(baseDirectoryPath, ORGANIZER_ROOT_NAME);
        String defaultReportDirectory = joinPath(baseDirectoryPath, REPORT_ROOT_NAME);
        if (!samePath(organizerRootPath, defaultOrganizerRoot)) {
            warnings.add("检测到当前范围已经位于“资料整理”目录内，本次会复用已有整理根目录，避免重复嵌套。");
        }
        if (candidates.skippedOrganizerOutputs()) {
            warnings.add("已自动跳过既有“资料整理”产物目录，避免把上一次整理结果再次当作待整理来源。");
        }
        if (candidates.usedOrganizerFallback()) {
            warnings.add("当前范围内没有普通原始资料，已改为在现有“资料整理”目录内继续整理，避免误判没有可用资料。");
        }
        if (generateExecutionReport && !samePath(reportDirectoryPath, defaultReportDirectory)) {
            warnings.add("检测到当前范围已经位于“执行报告”目录内，本次会复用已有报告根目录，避免重复嵌套。");
        }
        if (candidates.skippedReportOutputs()) {
            warnings.add("已自动跳过既有“执行报告”目录，避免把旧报告再次纳入整理范围。");
        }
        if (folders.size() == 1) {
            warnings.add("当前文件类型比较集中，本次可能只会归入一个分类目录。");
        }
        if (generateExecutionReport) {
            warnings.add("本次整理完成后会额外生成一份执行报告。");
        }

        return new WorkspaceOrganizationPlan(
                baseDirectoryPath,
                organizerRootPath,
                folders,
                moves,
                cleanupCandidates,
                scopeRoots,
                generateExecutionReport,
                reportDirectoryPath,
                reportFilePath,
                warnings
        );
    }

    public FolderPreparationResult ensureFolders(Long userId, WorkspaceOrganizationPlan plan) {
        for (WorkspaceOrganizationPlan.CategoryFolder folder : plan.folders()) {
            workspaceFinderService.ensureDirectory(userId, folder.path());
        }
        if (plan.generateExecutionReport() && StringUtils.hasText(plan.reportDirectoryPath())) {
            workspaceFinderService.ensureDirectory(userId, plan.reportDirectoryPath());
        }
        return new FolderPreparationResult(plan.organizerRootPath(), plan.folders().size());
    }

    public MoveExecutionResult moveFiles(Long userId, WorkspaceOrganizationPlan plan) {
        int movedCount = 0;
        List<String> movedTargets = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        for (WorkspaceOrganizationPlan.MoveOperation move : plan.moves()) {
            try {
                workspaceFinderService.ensureDirectory(userId, move.targetDirectoryPath());
                if (workspaceFinderService.exists(userId, move.targetPath())
                        && !workspaceFinderService.exists(userId, move.sourcePath())) {
                    movedTargets.add(move.targetPath());
                    movedCount++;
                    continue;
                }

                WorkspaceItemRef moved = workspaceFinderService.moveFileToPath(userId, move.sourcePath(), move.targetPath());
                movedTargets.add(moved.path());
                movedCount++;
            } catch (RuntimeException exception) {
                issues.add(move.sourceLabel() + "：" + exception.getMessage());
            }
        }

        return new MoveExecutionResult(movedCount, movedTargets, issues);
    }

    public CleanupExecutionResult cleanupEmptyFolders(Long userId, WorkspaceOrganizationPlan plan) {
        List<String> cleanupRoots = plan.scopeRoots().isEmpty() ? plan.cleanupCandidates() : plan.scopeRoots();
        List<String> preservedPaths = new ArrayList<>();
        if (!scopeRootsExplicitlyTarget(plan.scopeRoots(), plan.organizerRootPath())) {
            preservedPaths.add(plan.organizerRootPath());
        }
        if (StringUtils.hasText(plan.reportDirectoryPath())
                && !scopeRootsExplicitlyTarget(plan.scopeRoots(), plan.reportDirectoryPath())) {
            preservedPaths.add(plan.reportDirectoryPath());
        }

        List<String> deletedPaths = workspaceFinderService.deleteEmptyDirectoriesWithin(
                userId,
                cleanupRoots,
                preservedPaths
        );
        return new CleanupExecutionResult(deletedPaths);
    }

    public ReviewResult review(Long userId, WorkspaceOrganizationPlan plan) {
        int organizedCount = 0;
        List<String> issues = new ArrayList<>();

        for (WorkspaceOrganizationPlan.MoveOperation move : plan.moves()) {
            if (workspaceFinderService.exists(userId, move.targetPath())) {
                organizedCount++;
                continue;
            }
            if (workspaceFinderService.exists(userId, move.sourcePath())) {
                issues.add(move.sourceLabel() + " 仍留在原目录，尚未完成整理。");
            } else {
                issues.add(move.sourceLabel() + " 的目标文件未找到，请检查执行结果。");
            }
        }

        return new ReviewResult(organizedCount, plan.moves().size(), issues);
    }

    public ReportWriteResult writeExecutionReport(Long userId, WorkspaceOrganizationPlan plan, String prompt) {
        if (!plan.generateExecutionReport() || !StringUtils.hasText(plan.reportFilePath())) {
            return new ReportWriteResult(null, 0, 0, 0, List.of());
        }

        workspaceFinderService.ensureDirectory(userId, plan.reportDirectoryPath());
        ReviewResult reviewResult = review(userId, plan);
        List<String> deletedFolders = plan.cleanupCandidates().stream()
                .map(this::normalizePath)
                .filter(path -> !samePath(path, plan.organizerRootPath()))
                .filter(path -> !samePath(path, plan.reportDirectoryPath()))
                .filter(StringUtils::hasText)
                .filter(path -> !workspaceFinderService.exists(userId, path))
                .distinct()
                .toList();

        List<String> warnings = new ArrayList<>(plan.warnings());
        warnings.addAll(reviewResult.issues());

        String content = buildExecutionReportContent(prompt, plan, reviewResult, deletedFolders, warnings);
        WorkspaceItemRef written = workspaceFinderService.writeTextFile(userId, plan.reportFilePath(), content);

        return new ReportWriteResult(
                written.path(),
                plan.folders().size(),
                reviewResult.organizedCount(),
                deletedFolders.size(),
                warnings
        );
    }

    private List<WorkspaceItemRef> resolveOrganizationScope(boolean globalScope, List<WorkspaceItemRef> workingFiles) {
        if (globalScope) {
            return List.of(new WorkspaceItemRef(WORKSPACE_ROOT, "工作区", "folder"));
        }
        return workingFiles == null ? List.of() : workingFiles;
    }

    private String resolvePlanBaseDirectory(List<WorkspaceItemRef> selectedFiles, List<WorkspaceItemRef> organizationScope) {
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            return commonParentDirectory(selectedFiles.stream().map(WorkspaceItemRef::path).toList());
        }

        if (organizationScope == null || organizationScope.isEmpty()) {
            return WORKSPACE_ROOT;
        }

        List<String> scopeDirectories = organizationScope.stream()
                .filter(item -> item != null && StringUtils.hasText(item.path()))
                .map(item -> isFolder(item) ? item.path() : parentDirectoryOf(item.path()))
                .toList();
        return commonParentDirectory(scopeDirectories);
    }

    private String resolveManagedRootPath(String baseDirectoryPath, String rootName) {
        List<String> segments = new ArrayList<>(segmentsOf(baseDirectoryPath));
        int sameNameAncestorIndex = firstIndexOfSegment(segments, rootName);
        if (sameNameAncestorIndex >= 0) {
            return toVirtualPath(segments.subList(0, sameNameAncestorIndex + 1));
        }
        return joinPath(baseDirectoryPath, rootName);
    }

    private OrganizationCandidates selectOrganizationCandidates(
            List<WorkspaceItemRef> files,
            String organizerRootPath,
            boolean includeOrganizerRoot,
            String reportDirectoryPath,
            boolean includeReportDirectory
    ) {
        if (files == null || files.isEmpty()) {
            return new OrganizationCandidates(List.of(), false, false, false);
        }

        List<WorkspaceItemRef> withoutReports = files.stream()
                .filter(file -> includeReportDirectory || !isWithin(file.path(), reportDirectoryPath))
                .toList();
        boolean skippedReportOutputs = withoutReports.size() != files.size();

        if (includeOrganizerRoot) {
            return new OrganizationCandidates(withoutReports, false, skippedReportOutputs, false);
        }

        List<WorkspaceItemRef> preferredSources = withoutReports.stream()
                .filter(file -> !isWithin(file.path(), organizerRootPath))
                .toList();
        boolean skippedOrganizerOutputs = preferredSources.size() != withoutReports.size();

        if (!preferredSources.isEmpty()) {
            return new OrganizationCandidates(preferredSources, skippedOrganizerOutputs, skippedReportOutputs, false);
        }

        return new OrganizationCandidates(withoutReports, false, skippedReportOutputs, !withoutReports.isEmpty());
    }

    private boolean scopeExplicitlyTargets(List<WorkspaceItemRef> scope, String managedRootPath) {
        if (!StringUtils.hasText(managedRootPath) || scope == null || scope.isEmpty()) {
            return false;
        }

        return scope.stream()
                .filter(item -> item != null && StringUtils.hasText(item.path()))
                .map(item -> isFolder(item) ? item.path() : parentDirectoryOf(item.path()))
                .map(this::normalizePath)
                .anyMatch(path -> samePath(path, managedRootPath) || isWithin(path, managedRootPath));
    }

    private boolean scopeRootsExplicitlyTarget(List<String> scopeRoots, String managedRootPath) {
        if (!StringUtils.hasText(managedRootPath) || scopeRoots == null || scopeRoots.isEmpty()) {
            return false;
        }

        return scopeRoots.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizePath)
                .anyMatch(path -> samePath(path, managedRootPath) || isWithin(path, managedRootPath));
    }

    private int firstIndexOfSegment(List<String> segments, String expected) {
        for (int index = 0; index < segments.size(); index++) {
            if (expected.equals(segments.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private List<WorkspaceOrganizationPlan.MoveOperation> buildMovePlan(
            List<WorkspaceOrganizationPlan.CategoryFolder> folders,
            Map<String, List<WorkspaceItemRef>> categorized
    ) {
        Map<String, Set<String>> reservedNamesByDirectory = new LinkedHashMap<>();
        List<WorkspaceOrganizationPlan.MoveOperation> moves = new ArrayList<>();

        for (WorkspaceOrganizationPlan.CategoryFolder folder : folders) {
            List<WorkspaceItemRef> files = categorized.getOrDefault(folder.name(), List.of());
            for (WorkspaceItemRef file : files) {
                String fileName = basenameOf(file.path());
                String targetPath = uniqueTargetPath(folder.path(), fileName, reservedNamesByDirectory);
                moves.add(new WorkspaceOrganizationPlan.MoveOperation(
                        file.path(),
                        file.label(),
                        folder.name(),
                        folder.path(),
                        targetPath
                ));
            }
        }

        return List.copyOf(moves);
    }

    private String uniqueTargetPath(
            String directoryPath,
            String fileName,
            Map<String, Set<String>> reservedNamesByDirectory
    ) {
        Set<String> reservedNames = reservedNamesByDirectory.computeIfAbsent(directoryPath, ignored -> new LinkedHashSet<>());
        String extension = extensionOf(fileName);
        String stem = stripExtension(fileName);

        String candidateName = fileName;
        int suffix = 1;
        while (!reservedNames.add(candidateName.toLowerCase(Locale.ROOT))) {
            candidateName = extension.isBlank()
                    ? stem + "-" + suffix
                    : stem + "-" + suffix + "." + extension;
            suffix++;
        }
        return joinPath(directoryPath, candidateName);
    }

    private String categoryFor(WorkspaceItemRef file) {
        String extension = extensionOf(file.label());
        if (PAPER_EXTENSIONS.contains(extension)) {
            return "研究论文";
        }
        if (OFFICE_EXTENSIONS.contains(extension)) {
            return "项目文档";
        }
        if (NOTE_EXTENSIONS.contains(extension)) {
            return "笔记草稿";
        }
        if (DATA_EXTENSIONS.contains(extension)) {
            return "数据资料";
        }
        if (CODE_EXTENSIONS.contains(extension)) {
            return "代码脚本";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "图片素材";
        }
        return "待整理";
    }

    private String commonParentDirectory(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return WORKSPACE_ROOT;
        }

        List<List<String>> parentSegments = filePaths.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizePath)
                .map(this::parentDirectoryOf)
                .map(this::segmentsOf)
                .toList();

        if (parentSegments.isEmpty()) {
            return WORKSPACE_ROOT;
        }

        List<String> common = new ArrayList<>();
        int maxDepth = parentSegments.stream().mapToInt(List::size).min().orElse(0);
        for (int index = 0; index < maxDepth; index++) {
            String candidate = parentSegments.getFirst().get(index);
            boolean matches = true;
            for (List<String> parts : parentSegments) {
                if (!candidate.equals(parts.get(index))) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                break;
            }
            common.add(candidate);
        }
        return toVirtualPath(common);
    }

    private List<String> segmentsOf(String path) {
        if (!StringUtils.hasText(path) || WORKSPACE_ROOT.equals(path)) {
            return List.of();
        }

        String normalized = normalizePath(path);
        String relative = normalized.startsWith(WORKSPACE_ROOT)
                ? normalized.substring(WORKSPACE_ROOT.length())
                : normalized;
        if (!StringUtils.hasText(relative)) {
            return List.of();
        }
        return List.of(relative.split("/"));
    }

    private String toVirtualPath(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return WORKSPACE_ROOT;
        }
        return WORKSPACE_ROOT + String.join("/", segments);
    }

    private String parentDirectoryOf(WorkspaceItemRef item) {
        return parentDirectoryOf(item.path());
    }

    private String parentDirectoryOf(String virtualPath) {
        List<String> segments = new ArrayList<>(segmentsOf(virtualPath));
        if (segments.isEmpty()) {
            return WORKSPACE_ROOT;
        }
        segments.removeLast();
        return toVirtualPath(segments);
    }

    private String basenameOf(String virtualPath) {
        List<String> segments = segmentsOf(virtualPath);
        return segments.isEmpty() ? virtualPath : segments.getLast();
    }

    private String joinPath(String directoryPath, String leafName) {
        if (!StringUtils.hasText(leafName)) {
            return directoryPath;
        }
        if (!StringUtils.hasText(directoryPath) || WORKSPACE_ROOT.equals(directoryPath)) {
            return WORKSPACE_ROOT + leafName;
        }
        return normalizePath(directoryPath) + "/" + leafName;
    }

    private boolean samePath(String left, String right) {
        return normalizePath(left).equals(normalizePath(right));
    }

    private boolean isWithin(String candidatePath, String ancestorPath) {
        String candidate = normalizePath(candidatePath);
        String ancestor = normalizePath(ancestorPath);
        if (samePath(candidate, ancestor)) {
            return true;
        }
        if (WORKSPACE_ROOT.equals(ancestor)) {
            return true;
        }
        return candidate.startsWith(ancestor + "/");
    }

    private boolean isFolder(WorkspaceItemRef item) {
        return item != null && "folder".equalsIgnoreCase(item.kind());
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return WORKSPACE_ROOT;
        }
        if (WORKSPACE_ROOT.equals(path)) {
            return WORKSPACE_ROOT;
        }
        return path.endsWith("/") && path.length() > WORKSPACE_ROOT.length()
                ? path.substring(0, path.length() - 1)
                : path;
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String buildReportFileName(List<WorkspaceItemRef> workingFiles) {
        if (workingFiles == null || workingFiles.isEmpty()) {
            return "整理执行报告.md";
        }

        String label = workingFiles.stream()
                .map(WorkspaceItemRef::label)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("整理任务");
        return sanitizeFileName(stripExtension(label)) + "-整理执行报告.md";
    }

    private String buildExecutionReportContent(
            String prompt,
            WorkspaceOrganizationPlan plan,
            ReviewResult reviewResult,
            List<String> deletedFolders,
            List<String> warnings
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 资料整理执行报告\n\n");
        builder.append("## 任务说明\n");
        builder.append("- 执行任务：").append(StringUtils.hasText(prompt) ? prompt : "未提供任务描述").append('\n');
        builder.append("- 整理范围：").append(plan.baseDirectoryPath()).append('\n');
        builder.append("- 整理目录：").append(plan.organizerRootPath()).append('\n');
        builder.append("- 计划分类数：").append(plan.folders().size()).append('\n');
        builder.append("- 计划迁移文件数：").append(plan.moves().size()).append('\n');
        builder.append("- 清理空目录数：").append(deletedFolders.size()).append('\n');
        builder.append("- 复核通过文件数：").append(reviewResult.organizedCount())
                .append(" / ").append(reviewResult.totalCount()).append("\n\n");

        builder.append("## 分类目录\n");
        if (plan.folders().isEmpty()) {
            builder.append("- 本次没有生成新的分类目录。\n");
        } else {
            for (WorkspaceOrganizationPlan.CategoryFolder folder : plan.folders()) {
                builder.append("- ").append(folder.name())
                        .append("（").append(folder.fileCount())
                        .append(" 个文件，路径 ").append(folder.path()).append("）\n");
            }
        }
        builder.append('\n');

        builder.append("## 文件迁移记录\n");
        if (plan.moves().isEmpty()) {
            builder.append("- 本次没有迁移文件。\n");
        } else {
            for (WorkspaceOrganizationPlan.MoveOperation move : plan.moves()) {
                builder.append("- ").append(move.sourceLabel())
                        .append("（").append(move.sourcePath())
                        .append(" -> ").append(move.targetPath()).append("）\n");
            }
        }
        builder.append('\n');

        builder.append("## 清理结果\n");
        if (deletedFolders.isEmpty()) {
            builder.append("- 本次没有检测到可删除的空目录。\n");
        } else {
            builder.append("- 已清理空目录 ").append(deletedFolders.size()).append(" 个：")
                    .append(String.join("、", deletedFolders)).append("。\n");
        }
        builder.append('\n');

        builder.append("## 复核结果\n");
        if (reviewResult.issues().isEmpty()) {
            builder.append("- 复核通过，可继续使用新的目录结构。\n");
        } else {
            for (String issue : reviewResult.issues()) {
                builder.append("- ").append(issue).append('\n');
            }
        }
        builder.append('\n');

        if (!warnings.isEmpty()) {
            builder.append("## 提示与待确认项\n");
            warnings.stream().distinct().forEach(warning -> builder.append("- ").append(warning).append('\n'));
        }

        return builder.toString();
    }

    private String sanitizeFileName(String value) {
        if (!StringUtils.hasText(value)) {
            return "整理任务";
        }
        String sanitized = value.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
        return sanitized.isEmpty() ? "整理任务" : sanitized;
    }

    public record FolderPreparationResult(String organizerRootPath, int folderCount) {
    }

    public record MoveExecutionResult(int movedCount, List<String> movedTargets, List<String> issues) {
        public MoveExecutionResult {
            movedTargets = movedTargets == null ? List.of() : List.copyOf(movedTargets);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record CleanupExecutionResult(List<String> deletedPaths) {
        public CleanupExecutionResult {
            deletedPaths = deletedPaths == null ? List.of() : List.copyOf(deletedPaths);
        }
    }

    public record ReviewResult(int organizedCount, int totalCount, List<String> issues) {
        public ReviewResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record ReportWriteResult(
            String reportPath,
            int categoryCount,
            int movedCount,
            int deletedFolderCount,
            List<String> warnings
    ) {
        public ReportWriteResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    private record OrganizationCandidates(
            List<WorkspaceItemRef> selectedFiles,
            boolean skippedOrganizerOutputs,
            boolean skippedReportOutputs,
            boolean usedOrganizerFallback
    ) {
        private OrganizationCandidates {
            selectedFiles = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        }
    }
}
