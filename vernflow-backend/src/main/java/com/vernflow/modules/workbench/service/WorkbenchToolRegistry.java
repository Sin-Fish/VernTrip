package com.vernflow.modules.workbench.service;

import com.vernflow.modules.workbench.dto.WorkbenchDecisionRequest;
import com.vernflow.modules.workbench.model.WorkbenchToolSpec;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for tool-first workbench planning.
 */
@Service
public class WorkbenchToolRegistry {

    private static final List<WorkbenchToolSpec> TOOL_SPECS = List.of(
            new WorkbenchToolSpec(
                    "scope-read",
                    "读取范围",
                    "读取当前工作文件或全局工作区范围，为后续决策提供边界。",
                    "范围",
                    "读取当前范围",
                    "工作区范围",
                    "n1",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "local-retrieval",
                    "本地检索",
                    "在当前范围内召回高相关资料片段，准备证据输入。",
                    "检索",
                    "本地检索",
                    "证据召回",
                    "n2",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "web-supplement",
                    "网页补充",
                    "在范围不足时补充外部公开资料，并回并到主路径。",
                    "分支",
                    "网页补充",
                    "外部补充",
                    "branch",
                    true,
                    true,
                    false,
                    true
            ),
            new WorkbenchToolSpec(
                    "material-organize",
                    "材料整理",
                    "把证据与素材整理成结构化要点、提纲或资料包。",
                    "整理",
                    "整理材料",
                    "结构整理",
                    "n3",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "artifact-write",
                    "结果写入",
                    "根据整理结果生成工作区产物草稿，作为后续执行输出。",
                    "输出",
                    "生成结果草稿",
                    "结果草稿",
                    "n4",
                    false,
                    true,
                    true,
                    false
            ),
            new WorkbenchToolSpec(
                    "claim-inspection",
                    "结果检查",
                    "检查输出是否有证据绑定、覆盖率是否充分、断言是否稳健。",
                    "审查",
                    "结果检查",
                    "重点审查",
                    "n5",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "workspace-organize-plan",
                    "分类规划",
                    "根据当前工作文件规划资料整理目录，并生成可执行的移动方案。",
                    "规划",
                    "规划资料分类",
                    "建议目录结构",
                    "n2",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "workspace-move-files",
                    "移动文件",
                    "创建分类目录并把选中的资料移动到目标文件夹中。",
                    "执行",
                    "移动资料到新目录",
                    "执行文件迁移",
                    "n3",
                    false,
                    true,
                    true,
                    false
            ),
            new WorkbenchToolSpec(
                    "workspace-folder-cleanup",
                    "清理空目录",
                    "删除资料移动后残留的空文件夹，保持目录结构整洁。",
                    "清理",
                    "清理空文件夹",
                    "收尾清理",
                    "n4",
                    false,
                    true,
                    true,
                    false
            ),
            new WorkbenchToolSpec(
                    "workspace-review",
                    "整理复核",
                    "复核资料是否已经进入目标目录，并检查是否还有未整理残留。",
                    "复核",
                    "复核整理结果",
                    "整理检查",
                    "n5",
                    false,
                    true,
                    false,
                    false
            ),
            new WorkbenchToolSpec(
                    "workspace-write-report",
                    "执行报告",
                    "在整理完成后写入一份 Markdown 执行报告，记录目录变化与复核结果。",
                    "报告",
                    "生成执行报告",
                    "执行报告",
                    "n6",
                    false,
                    true,
                    true,
                    false
            )
    );

    private static final Map<String, WorkbenchToolSpec> TOOL_MAP = indexById();
    private static final List<String> CANONICAL_MAIN_ORDER = List.of(
            "scope-read",
            "local-retrieval",
            "material-organize",
            "artifact-write",
            "claim-inspection"
    );
    private static final List<String> REQUIRED_MAIN_TOOLS = List.of(
            "scope-read",
            "local-retrieval",
            "claim-inspection"
    );

    public List<WorkbenchToolSpec> listTools() {
        return TOOL_SPECS;
    }

    public List<WorkbenchDecisionRequest.ToolOption> listToolOptions() {
        return TOOL_SPECS.stream()
                .map(spec -> new WorkbenchDecisionRequest.ToolOption(
                        spec.id(),
                        spec.name(),
                        spec.summary(),
                        spec.typeLabel(),
                        spec.positionClass(),
                        spec.branchTool(),
                        spec.readWorkspace(),
                        spec.writeWorkspace(),
                        spec.useNetwork()
                ))
                .toList();
    }

    public WorkbenchToolSpec getRequired(String toolId) {
        WorkbenchToolSpec spec = TOOL_MAP.get(toolId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown workbench tool: " + toolId);
        }
        return spec;
    }

    public boolean contains(String toolId) {
        return TOOL_MAP.containsKey(toolId);
    }

    public List<String> canonicalMainOrder() {
        return CANONICAL_MAIN_ORDER;
    }

    public List<String> requiredMainTools() {
        return REQUIRED_MAIN_TOOLS;
    }

    private static Map<String, WorkbenchToolSpec> indexById() {
        Map<String, WorkbenchToolSpec> map = new LinkedHashMap<>();
        for (WorkbenchToolSpec spec : TOOL_SPECS) {
            map.put(spec.id(), spec);
        }
        return Map.copyOf(map);
    }
}
