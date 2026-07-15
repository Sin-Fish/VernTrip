package com.vernflow.modules.workbench.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.assistant.service.WorkspaceDocumentScopeService;
import com.vernflow.modules.rag.service.RagProfileFacade;
import com.vernflow.modules.workbench.model.WorkbenchPlan;
import com.vernflow.modules.workbench.model.WorkbenchRunRuntime;
import com.vernflow.modules.workbench.model.WorkspaceOrganizationPlan;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Executes concrete tool handlers for workbench plan nodes.
 */
@Service
public class WorkbenchToolExecutionService {

    private final WorkspaceDocumentScopeService workspaceDocumentScopeService;
    private final RagProfileFacade ragProfileFacade;
    private final WorkspaceOrganizationService workspaceOrganizationService;
    private final ObjectMapper objectMapper;

    public WorkbenchToolExecutionService(
            WorkspaceDocumentScopeService workspaceDocumentScopeService,
            RagProfileFacade ragProfileFacade,
            WorkspaceOrganizationService workspaceOrganizationService,
            ObjectMapper objectMapper
    ) {
        this.workspaceDocumentScopeService = workspaceDocumentScopeService;
        this.ragProfileFacade = ragProfileFacade;
        this.workspaceOrganizationService = workspaceOrganizationService;
        this.objectMapper = objectMapper;
    }

    public NodeExecutionOutcome executeNode(
            Long userId,
            String prompt,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            WorkbenchPlan.Node node
    ) {
        if (node == null) {
            return NodeExecutionOutcome.unsupported();
        }

        return switch (node.toolId()) {
            case "scope-read" -> executeScopeRead(userId, globalScope, workingFiles);
            case "local-retrieval" -> executeLocalRetrieval(userId, prompt, globalScope, workingFiles);
            case "workspace-organize-plan" -> executeWorkspaceOrganizePlan(node);
            case "workspace-move-files" -> executeWorkspaceMoveFiles(userId, node);
            case "workspace-folder-cleanup" -> executeWorkspaceFolderCleanup(userId, node);
            case "workspace-review" -> executeWorkspaceReview(userId, node);
            case "workspace-write-report" -> executeWorkspaceWriteReport(userId, prompt, node);
            default -> NodeExecutionOutcome.unsupported();
        };
    }

    private NodeExecutionOutcome executeScopeRead(
            Long userId,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles
    ) {
        WorkspaceDocumentScopeService.ScopeExtractionResult scope = workspaceDocumentScopeService.collectScope(
                userId,
                globalScope,
                workingFiles
        );

        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState(
                "on",
                "范围模式",
                globalScope ? "全局工作区" : "工作文件"
        ));
        checks.add(new WorkbenchRunRuntime.CheckState(
                "on",
                "范围文件",
                scope.totalScopeFileCount() + " 个"
        ));
        checks.add(new WorkbenchRunRuntime.CheckState(
                scope.readableDocuments().isEmpty() ? "warn" : "on",
                "可读文档",
                scope.readableDocuments().size() + " 份"
        ));
        if (!scope.unreadableFiles().isEmpty()) {
            checks.add(new WorkbenchRunRuntime.CheckState(
                    "warn",
                    "未读文件",
                    scope.unreadableFiles().size() + " 个"
            ));
        }

        List<String> issues = new ArrayList<>();
        if (scope.totalScopeFileCount() == 0) {
            issues.add("当前范围还没有可检索文件。");
        } else if (scope.readableDocuments().isEmpty()) {
            issues.add("当前范围里的文件暂时都无法抽取正文。");
        }

        String executeResult;
        if (scope.totalScopeFileCount() == 0) {
            executeResult = "当前范围没有可检索文件";
        } else {
            executeResult = "已锁定 " + scope.totalScopeFileCount() + " 个文件，其中 "
                    + scope.readableDocuments().size() + " 个可读";
        }

        return new NodeExecutionOutcome(executeResult, checks, issues, true);
    }

    private NodeExecutionOutcome executeLocalRetrieval(
            Long userId,
            String prompt,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles
    ) {
        WorkspaceDocumentScopeService.ScopeExtractionResult scope = workspaceDocumentScopeService.collectScope(
                userId,
                globalScope,
                workingFiles
        );

        if (scope.totalScopeFileCount() == 0) {
            return new NodeExecutionOutcome(
                    "当前范围没有可检索文件",
                    List.of(
                            new WorkbenchRunRuntime.CheckState("warn", "命中证据", "0 条"),
                            new WorkbenchRunRuntime.CheckState("warn", "建议", "先在资料库里补充工作文件")
                    ),
                    List.of("当前范围为空，暂时无法执行本地检索。"),
                    true
            );
        }

        List<AiPaperClient.RagChunk> chunks = ragProfileFacade.materializeScopeChunks(scope.readableDocuments());
        if (chunks.isEmpty()) {
            return new NodeExecutionOutcome(
                    "当前范围缺少可读文本",
                    List.of(
                            new WorkbenchRunRuntime.CheckState("warn", "可读文档", "0 份"),
                            new WorkbenchRunRuntime.CheckState(
                                    "warn",
                                    "建议",
                                    "补充 markdown、txt、docx、pdf 等可抽取文本的文件"
                            )
                    ),
                    List.of("当前范围内没有可直接检索的文本内容。"),
                    true
            );
        }

        List<AiPaperClient.RagEvidence> evidences = retrieveEvidence(
                userId,
                prompt,
                scope.readableDocuments(),
                globalScope,
                chunks
        );
        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState(
                evidences.isEmpty() ? "warn" : "on",
                "命中证据",
                evidences.size() + " 条"
        ));
        checks.add(new WorkbenchRunRuntime.CheckState(
                "on",
                "候选文档",
                scope.readableDocuments().size() + " 份"
        ));

        int maxEvidenceChecks = Math.min(3, evidences.size());
        for (int index = 0; index < maxEvidenceChecks; index++) {
            AiPaperClient.RagEvidence evidence = evidences.get(index);
            checks.add(new WorkbenchRunRuntime.CheckState(
                    "on",
                    "证据 " + (index + 1),
                    formatEvidence(evidence)
            ));
        }

        List<String> issues = new ArrayList<>();
        if (evidences.isEmpty()) {
            issues.add("当前问题在现有工作文件里没有召回到稳定证据。");
        }
        if (!scope.unreadableFiles().isEmpty()) {
            issues.add("有 " + scope.unreadableFiles().size() + " 个文件无法抽取正文，可能影响检索覆盖。");
            checks.add(new WorkbenchRunRuntime.CheckState(
                    "warn",
                    "未读文件",
                    scope.unreadableFiles().size() + " 个"
            ));
        }

        String executeResult = evidences.isEmpty()
                ? "没有召回到稳定证据"
                : "已召回 " + evidences.size() + " 条关键证据";

        return new NodeExecutionOutcome(executeResult, checks, issues, true);
    }

    private NodeExecutionOutcome executeWorkspaceOrganizePlan(WorkbenchPlan.Node node) {
        WorkspaceOrganizationPlan plan = deserializeOrganizationPlan(node.payloadJson());
        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState("on", "整理根目录", plan.organizerRootPath()));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "分类目录", plan.folders().size() + " 类"));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "待迁移文件", plan.moves().size() + " 个"));
        if (plan.generateExecutionReport()) {
            checks.add(new WorkbenchRunRuntime.CheckState("on", "执行报告", plan.reportFilePath()));
        }
        plan.folders().stream()
                .limit(4)
                .forEach(folder -> checks.add(new WorkbenchRunRuntime.CheckState(
                        "on",
                        folder.name(),
                        folder.fileCount() + " 个文件"
                )));

        List<String> issues = new ArrayList<>();
        if (plan.moves().isEmpty()) {
            issues.add("当前没有可整理的工作文件。");
        }
        for (String warning : plan.warnings()) {
            checks.add(new WorkbenchRunRuntime.CheckState("warn", "提示", warning));
        }

        String result = plan.moves().isEmpty()
                ? "当前没有可整理文件"
                : "已规划 " + plan.folders().size() + " 类目录，准备迁移 " + plan.moves().size() + " 个文件";
        return new NodeExecutionOutcome(result, checks, issues, true);
    }

    private NodeExecutionOutcome executeWorkspaceMoveFiles(Long userId, WorkbenchPlan.Node node) {
        WorkspaceOrganizationPlan plan = deserializeOrganizationPlan(node.payloadJson());
        WorkspaceOrganizationService.FolderPreparationResult folderResult =
                workspaceOrganizationService.ensureFolders(userId, plan);
        WorkspaceOrganizationService.MoveExecutionResult moveResult =
                workspaceOrganizationService.moveFiles(userId, plan);

        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState("on", "目标根目录", folderResult.organizerRootPath()));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "创建目录", folderResult.folderCount() + " 个"));
        checks.add(new WorkbenchRunRuntime.CheckState(
                moveResult.issues().isEmpty() ? "on" : "warn",
                "已迁移文件",
                moveResult.movedCount() + " / " + plan.moves().size()
        ));
        moveResult.movedTargets().stream()
                .limit(3)
                .forEach(path -> checks.add(new WorkbenchRunRuntime.CheckState("on", "落点", path)));

        String result = moveResult.movedCount() == 0
                ? "没有迁移任何文件"
                : "已迁移 " + moveResult.movedCount() + " 个文件到分类目录";
        return new NodeExecutionOutcome(result, checks, moveResult.issues(), true);
    }

    private NodeExecutionOutcome executeWorkspaceFolderCleanup(Long userId, WorkbenchPlan.Node node) {
        WorkspaceOrganizationPlan plan = deserializeOrganizationPlan(node.payloadJson());
        WorkspaceOrganizationService.CleanupExecutionResult cleanupResult =
                workspaceOrganizationService.cleanupEmptyFolders(userId, plan);

        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState(
                cleanupResult.deletedPaths().isEmpty() ? "wait" : "on",
                "已清理目录",
                cleanupResult.deletedPaths().size() + " 个"
        ));
        cleanupResult.deletedPaths().stream()
                .limit(4)
                .forEach(path -> checks.add(new WorkbenchRunRuntime.CheckState("on", "删除空目录", path)));

        String result = cleanupResult.deletedPaths().isEmpty()
                ? "没有检测到可删除的空目录"
                : "已清理 " + cleanupResult.deletedPaths().size() + " 个空目录";
        return new NodeExecutionOutcome(result, checks, List.of(), true);
    }

    private NodeExecutionOutcome executeWorkspaceReview(Long userId, WorkbenchPlan.Node node) {
        WorkspaceOrganizationPlan plan = deserializeOrganizationPlan(node.payloadJson());
        WorkspaceOrganizationService.ReviewResult reviewResult =
                workspaceOrganizationService.review(userId, plan);

        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState(
                reviewResult.issues().isEmpty() ? "on" : "warn",
                "归档完成",
                reviewResult.organizedCount() + " / " + reviewResult.totalCount()
        ));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "分类目录", plan.folders().size() + " 类"));
        reviewResult.issues().stream()
                .limit(4)
                .forEach(issue -> checks.add(new WorkbenchRunRuntime.CheckState("warn", "待复核", issue)));

        String result = reviewResult.issues().isEmpty()
                ? "资料整理已完成，可继续演示新的目录结构"
                : "资料整理已完成，但仍有 " + reviewResult.issues().size() + " 项待复核";
        return new NodeExecutionOutcome(result, checks, reviewResult.issues(), true);
    }

    private NodeExecutionOutcome executeWorkspaceWriteReport(Long userId, String prompt, WorkbenchPlan.Node node) {
        WorkspaceOrganizationPlan plan = deserializeOrganizationPlan(node.payloadJson());
        if (!plan.generateExecutionReport() || !StringUtils.hasText(plan.reportFilePath())) {
            return new NodeExecutionOutcome(
                    "本次任务未启用执行报告",
                    List.of(new WorkbenchRunRuntime.CheckState("wait", "报告状态", "未请求生成")),
                    List.of(),
                    true
            );
        }

        WorkspaceOrganizationService.ReportWriteResult reportResult =
                workspaceOrganizationService.writeExecutionReport(userId, plan, prompt);

        List<WorkbenchRunRuntime.CheckState> checks = new ArrayList<>();
        checks.add(new WorkbenchRunRuntime.CheckState("on", "报告目录", plan.reportDirectoryPath()));
        checks.add(new WorkbenchRunRuntime.CheckState(
                StringUtils.hasText(reportResult.reportPath()) ? "on" : "warn",
                "报告文件",
                StringUtils.hasText(reportResult.reportPath()) ? reportResult.reportPath() : plan.reportFilePath()
        ));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "分类目录", reportResult.categoryCount() + " 类"));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "迁移文件", reportResult.movedCount() + " 个"));
        checks.add(new WorkbenchRunRuntime.CheckState("on", "清理空目录", reportResult.deletedFolderCount() + " 个"));
        reportResult.warnings().stream()
                .distinct()
                .limit(4)
                .forEach(warning -> checks.add(new WorkbenchRunRuntime.CheckState("warn", "提示", warning)));

        List<String> issues = StringUtils.hasText(reportResult.reportPath())
                ? List.of()
                : List.of("执行报告未能成功写入目标目录。");

        String result = StringUtils.hasText(reportResult.reportPath())
                ? "已写入执行报告：" + reportResult.reportPath()
                : "执行报告生成完成";
        return new NodeExecutionOutcome(result, checks, issues, true);
    }

    private WorkspaceOrganizationPlan deserializeOrganizationPlan(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            throw new BusinessException("Missing workspace organization payload.");
        }
        try {
            return objectMapper.readValue(payloadJson, WorkspaceOrganizationPlan.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to read workspace organization payload.");
        }
    }

    private List<AiPaperClient.RagEvidence> retrieveEvidence(
            Long userId,
            String prompt,
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> readableDocuments,
            boolean globalScope,
            List<AiPaperClient.RagChunk> chunks
    ) {
        try {
            return ragProfileFacade.retrieveFromScope(userId, prompt, readableDocuments, globalScope).evidences();
        } catch (BusinessException exception) {
            return fallbackRetrieveEvidence(prompt, chunks);
        }
    }

    private List<AiPaperClient.RagEvidence> fallbackRetrieveEvidence(
            String prompt,
            List<AiPaperClient.RagChunk> chunks
    ) {
        Set<String> keywords = tokenize(prompt);
        if (keywords.isEmpty()) {
            return chunks.stream()
                    .limit(3)
                    .map(chunk -> new AiPaperClient.RagEvidence(
                            chunk.paperId(),
                            chunk.paperTitle(),
                            chunk.sectionTitle(),
                            chunk.pageNo(),
                            excerpt(chunk.chunkText()),
                            0.0
                    ))
                    .toList();
        }

        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, scoreChunk(chunk, keywords)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(6)
                .map(scored -> new AiPaperClient.RagEvidence(
                        scored.chunk().paperId(),
                        scored.chunk().paperTitle(),
                        scored.chunk().sectionTitle(),
                        scored.chunk().pageNo(),
                        excerpt(scored.chunk().chunkText()),
                        roundScore(scored.score())
                ))
                .toList();
    }

    private double scoreChunk(AiPaperClient.RagChunk chunk, Set<String> keywords) {
        String haystack = (chunk.paperTitle() + " " + nullSafe(chunk.sectionTitle()) + " " + chunk.chunkText())
                .toLowerCase(Locale.ROOT);
        double score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                score += keyword.length() >= 4 ? 1.3 : 0.8;
            }
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }

        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (token.length() >= 2
                    || token.codePoints().anyMatch(codePoint ->
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String formatEvidence(AiPaperClient.RagEvidence evidence) {
        StringBuilder builder = new StringBuilder();
        builder.append(evidence.paperTitle());
        if (StringUtils.hasText(evidence.sectionTitle())) {
            builder.append(" / ").append(evidence.sectionTitle());
        }
        if (evidence.pageNo() != null) {
            builder.append(" / 第").append(evidence.pageNo()).append(" 页");
        }
        builder.append("（").append(excerpt(evidence.excerpt())).append("）");
        return builder.toString();
    }

    private String excerpt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 72 ? normalized.substring(0, 72) + "..." : normalized;
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    private double roundScore(double score) {
        return Math.round(score * 10_000d) / 10_000d;
    }

    public record NodeExecutionOutcome(
            String executeResult,
            List<WorkbenchRunRuntime.CheckState> checks,
            List<String> issues,
            boolean handled
    ) {
        public NodeExecutionOutcome {
            checks = checks == null ? List.of() : List.copyOf(checks);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public static NodeExecutionOutcome unsupported() {
            return new NodeExecutionOutcome(null, List.of(), List.of(), false);
        }
    }

    private record ScoredChunk(AiPaperClient.RagChunk chunk, double score) {
    }
}
