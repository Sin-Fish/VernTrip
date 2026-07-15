package com.vernflow.modules.workbench.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.assistant.service.WorkspaceDocumentScopeService;
import com.vernflow.modules.workbench.dto.WorkbenchDecisionRequest;
import com.vernflow.modules.workbench.dto.WorkbenchDecisionResponse;
import com.vernflow.modules.workbench.model.WorkbenchPlan;
import com.vernflow.modules.workbench.model.WorkbenchToolSpec;
import com.vernflow.modules.workbench.model.WorkspaceOrganizationPlan;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decision-only planner that generates a validated tool graph before execution starts.
 */
@Service
public class WorkbenchDecisionAgentService {

    private static final int MAX_DOCUMENT_PREVIEWS = 8;
    private static final int MAX_EXCERPT_LENGTH = 220;

    private static final List<String> NETWORK_HINTS = List.of(
            "网页", "网络", "外部", "搜索", "补充", "最新", "竞品", "行业",
            "web", "network", "external", "search", "latest", "industry"
    );
    private static final List<String> INSPECT_HINTS = List.of(
            "检查", "核对", "审查", "引用", "证据", "断言", "准确",
            "check", "inspect", "review", "citation", "evidence", "verify"
    );
    private static final List<String> WRITE_HINTS = List.of(
            "写", "生成", "报告", "总结", "方案", "文档", "稿", "输出",
            "write", "generate", "report", "summary", "document", "draft", "output"
    );

    private final WorkspaceDocumentScopeService workspaceDocumentScopeService;
    private final WorkbenchToolRegistry toolRegistry;
    private final AiPaperClient aiPaperClient;
    private final WorkspaceOrganizationService workspaceOrganizationService;
    private final ObjectMapper objectMapper;

    public WorkbenchDecisionAgentService(
            WorkspaceDocumentScopeService workspaceDocumentScopeService,
            WorkbenchToolRegistry toolRegistry,
            AiPaperClient aiPaperClient,
            WorkspaceOrganizationService workspaceOrganizationService,
            ObjectMapper objectMapper
    ) {
        this.workspaceDocumentScopeService = workspaceDocumentScopeService;
        this.toolRegistry = toolRegistry;
        this.aiPaperClient = aiPaperClient;
        this.workspaceOrganizationService = workspaceOrganizationService;
        this.objectMapper = objectMapper;
    }

    public WorkbenchPlan plan(Long userId, String prompt, boolean globalScope, List<WorkspaceItemRef> workingFiles) {
        WorkspaceDocumentScopeService.ScopeExtractionResult scopeResult =
                workspaceDocumentScopeService.collectScope(userId, globalScope, workingFiles);
        WorkbenchDecisionRequest request = buildDecisionRequest(prompt, globalScope, workingFiles, scopeResult);

        WorkbenchDecisionResponse response;
        try {
            response = aiPaperClient.planWorkbench(request);
        } catch (RuntimeException ignored) {
            response = null;
        }

        return buildPlan(request, normalizeResponse(response, request));
    }

    public WorkbenchPlan planWorkspaceOrganization(
            Long userId,
            String prompt,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            boolean generateExecutionReport
    ) {
        return buildWorkspaceOrganizationPlan(userId, prompt, globalScope, workingFiles, generateExecutionReport);
    }

    private WorkbenchPlan buildWorkspaceOrganizationPlan(
            Long userId,
            String prompt,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            boolean generateExecutionReport
    ) {
        WorkspaceOrganizationPlan organizationPlan = workspaceOrganizationService.plan(
                userId,
                globalScope,
                workingFiles,
                generateExecutionReport
        );
        String payloadJson = serializeOrganizationPlan(organizationPlan);

        List<WorkbenchPlan.Node> nodes = new ArrayList<>();
        nodes.add(new WorkbenchPlan.Node(
                "scope",
                "scope-read",
                "范围",
                "锁定整理范围",
                globalScope ? "当前使用全局工作区" : "当前使用工作文件范围",
                "n1",
                false,
                List.of(
                        new WorkbenchPlan.Check("on", "工作文件", organizationPlan.moves().size() + " 个待整理文件"),
                        new WorkbenchPlan.Check("on", "整理根目录", organizationPlan.organizerRootPath())
                ),
                payloadJson
        ));
        nodes.add(new WorkbenchPlan.Node(
                "organizer",
                "workspace-organize-plan",
                "规划",
                "规划资料分类",
                organizationPlan.folders().isEmpty()
                        ? "当前没有可整理文件"
                        : "建议新建 " + organizationPlan.folders().size() + " 类目录",
                "n2",
                false,
                buildOrganizationChecks(organizationPlan),
                payloadJson
        ));
        nodes.add(new WorkbenchPlan.Node(
                "mover",
                "workspace-move-files",
                "执行",
                "移动资料到新目录",
                organizationPlan.moves().isEmpty()
                        ? "当前没有可移动文件"
                        : "计划迁移 " + organizationPlan.moves().size() + " 个文件",
                "n3",
                false,
                List.of(
                        new WorkbenchPlan.Check("on", "目标目录", organizationPlan.organizerRootPath()),
                        new WorkbenchPlan.Check("on", "迁移文件", organizationPlan.moves().size() + " 个")
                ),
                payloadJson
        ));
        nodes.add(new WorkbenchPlan.Node(
                "cleanup",
                "workspace-folder-cleanup",
                "清理",
                "删除空文件夹",
                organizationPlan.cleanupCandidates().isEmpty()
                        ? "没有待清理的原目录"
                        : "候选空目录 " + organizationPlan.cleanupCandidates().size() + " 个",
                "n4",
                false,
                List.of(
                        new WorkbenchPlan.Check("on", "清理策略", "删除整理范围内已腾空的目录"),
                        new WorkbenchPlan.Check(
                                organizationPlan.cleanupCandidates().isEmpty() ? "wait" : "on",
                                "候选目录",
                                organizationPlan.cleanupCandidates().size() + " 个"
                        )
                ),
                payloadJson
        ));
        nodes.add(new WorkbenchPlan.Node(
                "review",
                "workspace-review",
                "复核",
                "复核整理结果",
                "检查目标目录是否完整，确认是否还有残留文件",
                "n5",
                false,
                List.of(
                        new WorkbenchPlan.Check("on", "复核重点", "文件是否到位、空目录是否清理"),
                        new WorkbenchPlan.Check("warn", "人工复核", "建议抽查至少一类目录")
                ),
                payloadJson
        ));
        if (organizationPlan.generateExecutionReport()) {
            nodes.add(new WorkbenchPlan.Node(
                    "report",
                    "workspace-write-report",
                    "报告",
                    "生成执行报告",
                    StringUtils.hasText(organizationPlan.reportFilePath())
                            ? "将报告写入 " + organizationPlan.reportFilePath()
                            : "整理完成后输出一份执行报告",
                    "n6",
                    false,
                    List.of(
                            new WorkbenchPlan.Check("on", "报告目录", organizationPlan.reportDirectoryPath()),
                            new WorkbenchPlan.Check(
                                    "on",
                                    "报告文件",
                                    StringUtils.hasText(organizationPlan.reportFilePath())
                                            ? organizationPlan.reportFilePath()
                                            : "执行报告.md"
                            ),
                            new WorkbenchPlan.Check("on", "输出格式", "Markdown")
                    ),
                    payloadJson
            ));
        }

        List<WorkbenchPlan.Link> links = new ArrayList<>(List.of(
                new WorkbenchPlan.Link("scope", "organizer", false),
                new WorkbenchPlan.Link("organizer", "mover", false),
                new WorkbenchPlan.Link("mover", "cleanup", false),
                new WorkbenchPlan.Link("cleanup", "review", false)
        ));
        if (organizationPlan.generateExecutionReport()) {
            links.add(new WorkbenchPlan.Link("review", "report", false));
        }

        List<String> mainPath = new ArrayList<>(List.of("scope", "organizer", "mover", "cleanup", "review"));
        if (organizationPlan.generateExecutionReport()) {
            mainPath.add("report");
        }

        String toolChain = organizationPlan.generateExecutionReport()
                ? "读取范围 / 分类规划 / 移动文件 / 清理空目录 / 整理复核 / 执行报告"
                : "读取范围 / 分类规划 / 移动文件 / 清理空目录 / 整理复核";

        return new WorkbenchPlan(
                sanitizeTitle(null, prompt, workingFiles.stream().map(WorkspaceItemRef::label).toList()),
                buildOrganizationSummary(organizationPlan),
                toolChain,
                "focus",
                "organizer",
                mainPath,
                nodes,
                links
        );
    }

    private List<WorkbenchPlan.Check> buildOrganizationChecks(WorkspaceOrganizationPlan plan) {
        List<WorkbenchPlan.Check> checks = new ArrayList<>();
        checks.add(new WorkbenchPlan.Check("on", "整理根目录", plan.organizerRootPath()));
        if (plan.folders().isEmpty()) {
            checks.add(new WorkbenchPlan.Check("warn", "分类结果", "当前没有可整理的文件"));
        } else {
            for (WorkspaceOrganizationPlan.CategoryFolder folder : plan.folders()) {
                checks.add(new WorkbenchPlan.Check("on", folder.name(), folder.fileCount() + " 个文件"));
            }
        }
        if (plan.generateExecutionReport()) {
            checks.add(new WorkbenchPlan.Check("on", "执行报告", "整理完成后追加一份 Markdown 报告"));
        }
        plan.warnings().forEach(warning -> checks.add(new WorkbenchPlan.Check("warn", "提示", warning)));
        return List.copyOf(checks);
    }

    private String buildOrganizationSummary(WorkspaceOrganizationPlan plan) {
        if (plan.moves().isEmpty()) {
            return "当前工作文件为空，先选择资料后再执行资料库整理。";
        }
        String summary = "将在 " + plan.organizerRootPath()
                + " 下整理 " + plan.moves().size() + " 个文件，分类为 "
                + Math.max(plan.folders().size(), 1) + " 类目录。";
        if (plan.generateExecutionReport()) {
            summary += " 整理完成后还会在 " + plan.reportDirectoryPath() + " 写入一份执行报告。";
        }
        return summary;
    }

    private String serializeOrganizationPlan(WorkspaceOrganizationPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist workspace organization plan payload.", exception);
        }
    }

    private WorkbenchDecisionRequest buildDecisionRequest(
            String prompt,
            boolean globalScope,
            List<WorkspaceItemRef> workingFiles,
            WorkspaceDocumentScopeService.ScopeExtractionResult scopeResult
    ) {
        List<String> workingLabels = workingFiles.stream()
                .map(WorkspaceItemRef::label)
                .filter(StringUtils::hasText)
                .toList();

        List<WorkbenchDecisionRequest.DocumentPreview> documents = scopeResult.readableDocuments().stream()
                .limit(MAX_DOCUMENT_PREVIEWS)
                .map(document -> new WorkbenchDecisionRequest.DocumentPreview(
                        document.path(),
                        document.citeLabel(),
                        document.sectionTitle(),
                        document.chunks().size(),
                        abbreviate(document.chunks().isEmpty() ? "" : document.chunks().getFirst(), MAX_EXCERPT_LENGTH)
                ))
                .toList();

        return new WorkbenchDecisionRequest(
                prompt,
                globalScope ? "global_workspace" : "working_files",
                workingFiles.size(),
                scopeResult.totalScopeFileCount(),
                scopeResult.readableDocuments().size(),
                workingLabels,
                scopeResult.unreadableFiles(),
                documents,
                toolRegistry.listToolOptions()
        );
    }

    private WorkbenchDecisionResponse normalizeResponse(
            WorkbenchDecisionResponse raw,
            WorkbenchDecisionRequest request
    ) {
        if (raw == null) {
            return fallbackResponse(request);
        }

        LinkedHashSet<String> mainTools = sanitizeTools(raw.mainTools(), false);
        LinkedHashSet<String> branchTools = sanitizeTools(raw.branchTools(), true);

        for (String required : toolRegistry.requiredMainTools()) {
            mainTools.add(required);
        }

        if (shouldPreferWriter(request.prompt())) {
            mainTools.add("material-organize");
            mainTools.add("artifact-write");
        } else if (!mainTools.contains("material-organize") && !mainTools.contains("artifact-write")) {
            mainTools.add("material-organize");
        }

        if (branchTools.contains("web-supplement")) {
            mainTools.add("material-organize");
        }

        List<String> orderedMainTools = toolRegistry.canonicalMainOrder().stream()
                .filter(mainTools::contains)
                .toList();

        if (orderedMainTools.size() < 3) {
            return fallbackResponse(request);
        }

        List<WorkbenchDecisionResponse.NodeNote> nodeNotes = raw.nodeNotes().stream()
                .filter(note -> note != null && toolRegistry.contains(note.toolId()))
                .filter(note -> orderedMainTools.contains(note.toolId()) || branchTools.contains(note.toolId()))
                .toList();

        String inspectMode = normalizeInspectMode(raw.inspectMode(), request.prompt());
        String title = sanitizeTitle(raw.title(), request.prompt(), request.workingFileLabels());
        String summary = sanitizeSummary(raw.summary(), request, orderedMainTools, branchTools);

        return new WorkbenchDecisionResponse(
                title,
                summary,
                inspectMode,
                orderedMainTools,
                List.copyOf(branchTools),
                nodeNotes
        );
    }

    private WorkbenchDecisionResponse fallbackResponse(WorkbenchDecisionRequest request) {
        boolean needsWeb = containsAny(request.prompt(), NETWORK_HINTS);
        boolean includeWriter = shouldPreferWriter(request.prompt());

        List<String> mainTools = new ArrayList<>();
        mainTools.add("scope-read");
        mainTools.add("local-retrieval");
        mainTools.add("material-organize");
        if (includeWriter) {
            mainTools.add("artifact-write");
        }
        mainTools.add("claim-inspection");

        List<String> branchTools = needsWeb ? List.of("web-supplement") : List.of();
        String scopeText = "global_workspace".equals(request.scopeMode()) ? "全局工作区" : "工作文件范围";
        String branchText = branchTools.isEmpty() ? "" : "，必要时走外部分支补充";
        String writeText = includeWriter ? "，生成结果草稿" : "";
        String summary = "先读取 " + scopeText + "，再检索并整理证据" + writeText + branchText + "，最后做结果检查。";

        Map<String, WorkbenchDecisionResponse.NodeNote> notes = new LinkedHashMap<>();
        notes.put("scope-read", new WorkbenchDecisionResponse.NodeNote(
                "scope-read",
                "读取当前范围",
                "锁定本次任务的 " + scopeText,
                "先确认边界，避免后续节点跨范围执行。"
        ));
        notes.put("local-retrieval", new WorkbenchDecisionResponse.NodeNote(
                "local-retrieval",
                "本地检索",
                request.readableDocumentCount() > 0
                        ? "优先在可读资料中召回关键证据"
                        : "当前可读资料较少，先尝试基础召回",
                "把后续整理和检查建立在证据召回之上。"
        ));
        notes.put("material-organize", new WorkbenchDecisionResponse.NodeNote(
                "material-organize",
                "整理材料",
                "按任务目标整理结构、要点与引用线索",
                "先做结构化整理，再决定是否写出产物。"
        ));
        if (includeWriter) {
            notes.put("artifact-write", new WorkbenchDecisionResponse.NodeNote(
                    "artifact-write",
                    "生成结果草稿",
                    "把整理后的材料写成工作区输出草稿",
                    "输出草稿后，检查节点才有明确产物可以核对。"
            ));
        }
        notes.put("claim-inspection", new WorkbenchDecisionResponse.NodeNote(
                "claim-inspection",
                "结果检查",
                "检查覆盖率、断言稳健性和引用绑定",
                "最后确认输出有没有脱离证据。"
        ));
        if (needsWeb) {
            notes.put("web-supplement", new WorkbenchDecisionResponse.NodeNote(
                    "web-supplement",
                    "网页补充",
                    "在本地证据不足时补充公开资料",
                    "作为分支工具使用，避免一开始就扩大噪声范围。"
            ));
        }

        return new WorkbenchDecisionResponse(
                sanitizeTitle(null, request.prompt(), request.workingFileLabels()),
                summary,
                normalizeInspectMode(null, request.prompt()),
                mainTools,
                branchTools,
                List.copyOf(notes.values())
        );
    }

    private WorkbenchPlan buildPlan(WorkbenchDecisionRequest request, WorkbenchDecisionResponse response) {
        Map<String, WorkbenchDecisionResponse.NodeNote> noteMap = new LinkedHashMap<>();
        for (WorkbenchDecisionResponse.NodeNote note : response.nodeNotes()) {
            noteMap.put(note.toolId(), note);
        }

        List<String> mainPath = response.mainTools().stream().map(this::nodeIdForTool).toList();
        String branchTool = response.branchTools().stream().findFirst().orElse(null);
        List<WorkbenchPlan.Node> nodes = new ArrayList<>();

        for (String toolId : response.mainTools()) {
            WorkbenchToolSpec spec = toolRegistry.getRequired(toolId);
            nodes.add(buildNode(spec, noteMap.get(toolId), request, false));
        }
        if (branchTool != null) {
            nodes.add(buildNode(toolRegistry.getRequired(branchTool), noteMap.get(branchTool), request, true));
        }

        List<WorkbenchPlan.Link> links = new ArrayList<>();
        for (int index = 0; index < mainPath.size() - 1; index += 1) {
            links.add(new WorkbenchPlan.Link(mainPath.get(index), mainPath.get(index + 1), false));
        }

        if (branchTool != null) {
            String branchNodeId = nodeIdForTool(branchTool);
            if (mainPath.contains("retriever")) {
                links.add(new WorkbenchPlan.Link("retriever", branchNodeId, true));
            }

            String branchTarget = response.mainTools().contains("artifact-write")
                    ? "writer"
                    : response.mainTools().contains("material-organize")
                    ? "selector"
                    : mainPath.getLast();
            links.add(new WorkbenchPlan.Link(branchNodeId, branchTarget, false));
        }

        String selectedNodeId = response.mainTools().contains("material-organize")
                ? "selector"
                : response.mainTools().contains("artifact-write")
                ? "writer"
                : mainPath.getFirst();
        String toolChain = response.mainTools().stream()
                .map(toolRegistry::getRequired)
                .map(WorkbenchToolSpec::name)
                .reduce((left, right) -> left + " / " + right)
                .orElse("读取范围 / 本地检索 / 结果检查");

        return new WorkbenchPlan(
                response.title(),
                response.summary(),
                toolChain,
                response.inspectMode(),
                selectedNodeId,
                mainPath,
                nodes,
                links
        );
    }

    private WorkbenchPlan.Node buildNode(
            WorkbenchToolSpec spec,
            WorkbenchDecisionResponse.NodeNote note,
            WorkbenchDecisionRequest request,
            boolean branchNode
    ) {
        String title = StringUtils.hasText(note == null ? null : note.title()) ? note.title().trim() : spec.defaultTitle();
        String detail = StringUtils.hasText(note == null ? null : note.detail())
                ? note.detail().trim()
                : defaultDetail(spec.id(), request);
        String rationale = StringUtils.hasText(note == null ? null : note.rationale())
                ? note.rationale().trim()
                : defaultRationale(spec.id(), request);

        List<WorkbenchPlan.Check> checks = new ArrayList<>();
        checks.add(new WorkbenchPlan.Check("on", "决策说明", rationale));
        if ("scope-read".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check(
                    "on",
                    "当前范围",
                    "global_workspace".equals(request.scopeMode()) ? "全局工作区" : "工作文件"
            ));
            checks.add(new WorkbenchPlan.Check("on", "范围文件数", Long.toString(request.totalScopeFileCount())));
        } else if ("local-retrieval".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check("on", "可读资料数", Long.toString(request.readableDocumentCount())));
            checks.add(new WorkbenchPlan.Check("on", "检索策略", "本地证据优先"));
        } else if ("web-supplement".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check("warn", "触发条件", "仅在本地证据不足时补充"));
            checks.add(new WorkbenchPlan.Check("on", "网络访问", "已隔离到分支"));
        } else if ("material-organize".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check("on", "整理目标", "提炼结构与要点"));
            checks.add(new WorkbenchPlan.Check("on", "输入来源", "证据片段"));
        } else if ("artifact-write".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check("on", "输出形态", "工作区结果草稿"));
            checks.add(new WorkbenchPlan.Check("on", "落盘权限", "已授权"));
        } else if ("claim-inspection".equals(spec.id())) {
            checks.add(new WorkbenchPlan.Check("on", "审查模式", normalizeInspectMode(null, request.prompt())));
            checks.add(new WorkbenchPlan.Check("warn", "检查焦点", "引用绑定与覆盖率"));
        }

        return new WorkbenchPlan.Node(
                nodeIdForTool(spec.id()),
                spec.id(),
                spec.typeLabel(),
                title,
                detail,
                spec.positionClass(),
                branchNode,
                checks,
                null
        );
    }

    private LinkedHashSet<String> sanitizeTools(List<String> toolIds, boolean branchTools) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (toolIds == null) {
            return normalized;
        }

        for (String toolId : toolIds) {
            if (!StringUtils.hasText(toolId) || !toolRegistry.contains(toolId)) {
                continue;
            }
            WorkbenchToolSpec spec = toolRegistry.getRequired(toolId);
            if (spec.branchTool() == branchTools) {
                normalized.add(toolId.trim());
            }
        }
        return normalized;
    }

    private String sanitizeTitle(String title, String prompt, List<String> workingFileLabels) {
        if (StringUtils.hasText(title)) {
            return abbreviate(title.trim(), 42);
        }
        if (StringUtils.hasText(prompt)) {
            return abbreviate(prompt.trim().replaceAll("\\s+", " "), 42);
        }
        if (!workingFileLabels.isEmpty()) {
            return abbreviate("资料任务 - " + workingFileLabels.getFirst(), 42);
        }
        return "资料任务规划";
    }

    private String sanitizeSummary(
            String summary,
            WorkbenchDecisionRequest request,
            List<String> mainTools,
            LinkedHashSet<String> branchTools
    ) {
        if (StringUtils.hasText(summary)) {
            return abbreviate(summary.trim(), 120);
        }

        String scopeText = "global_workspace".equals(request.scopeMode()) ? "全局工作区" : "工作文件";
        String branchText = branchTools.isEmpty() ? "" : "，必要时走外部分支补充";
        String writeText = mainTools.contains("artifact-write") ? "，生成结果草稿" : "";
        return "先读取 " + scopeText + "并做本地检索，整理关键材料" + writeText + branchText + "，最后执行结果检查。";
    }

    private String normalizeInspectMode(String inspectMode, String prompt) {
        if ("full".equalsIgnoreCase(inspectMode) || "focus".equalsIgnoreCase(inspectMode)) {
            return inspectMode.toLowerCase(Locale.ROOT);
        }
        return containsAny(prompt, INSPECT_HINTS) ? "full" : "focus";
    }

    private boolean shouldPreferWriter(String prompt) {
        return containsAny(prompt, WRITE_HINTS) || !containsAny(prompt, INSPECT_HINTS);
    }

    private boolean containsAny(String text, List<String> hints) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return hints.stream().anyMatch(normalized::contains);
    }

    private String defaultDetail(String toolId, WorkbenchDecisionRequest request) {
        return switch (toolId) {
            case "scope-read" -> "global_workspace".equals(request.scopeMode()) ? "全局工作区" : request.workingFileCount() + " 份资料";
            case "local-retrieval" -> "可读资料 " + request.readableDocumentCount() + " 份";
            case "web-supplement" -> "外部补充分支";
            case "material-organize" -> "结构化整理";
            case "artifact-write" -> "工作区结果草稿";
            case "claim-inspection" -> normalizeInspectMode(null, request.prompt());
            default -> "待规划";
        };
    }

    private String defaultRationale(String toolId, WorkbenchDecisionRequest request) {
        return switch (toolId) {
            case "scope-read" -> "先确认输入边界，再规划后续链路。";
            case "local-retrieval" -> "后续整理和检查都依赖证据召回结果。";
            case "web-supplement" -> "只在本地范围不足时再扩大信息源。";
            case "material-organize" -> "先把证据整理成结构，避免直接写出松散结果。";
            case "artifact-write" -> "输出成草稿后，后续检查才有明确对象。";
            case "claim-inspection" -> "最后确认覆盖率、引用绑定和断言稳健性。";
            default -> "按当前任务需要保留此节点。";
        };
    }

    private String nodeIdForTool(String toolId) {
        return switch (toolId) {
            case "scope-read" -> "scope";
            case "local-retrieval" -> "retriever";
            case "web-supplement" -> "branch";
            case "material-organize" -> "selector";
            case "artifact-write" -> "writer";
            case "claim-inspection" -> "audit";
            case "workspace-organize-plan" -> "organizer";
            case "workspace-move-files" -> "mover";
            case "workspace-folder-cleanup" -> "cleanup";
            case "workspace-review" -> "review";
            case "workspace-write-report" -> "report";
            default -> toolId;
        };
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
