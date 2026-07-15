package com.vernflow.modules.workbench.model;

/**
 * Registered workbench tool metadata used for planning and graph materialization.
 *
 * @param id tool id exposed to the planner
 * @param name display name
 * @param summary short capability summary
 * @param typeLabel node type label
 * @param defaultTitle default node title
 * @param defaultDetail default node detail
 * @param positionClass frontend position slot
 * @param branchTool whether this tool belongs to an off-path branch
 * @param readWorkspace whether the tool reads workspace data
 * @param writeWorkspace whether the tool writes workspace data
 * @param useNetwork whether the tool may access network resources
 */
public record WorkbenchToolSpec(
        String id,
        String name,
        String summary,
        String typeLabel,
        String defaultTitle,
        String defaultDetail,
        String positionClass,
        boolean branchTool,
        boolean readWorkspace,
        boolean writeWorkspace,
        boolean useNetwork
) {
}
