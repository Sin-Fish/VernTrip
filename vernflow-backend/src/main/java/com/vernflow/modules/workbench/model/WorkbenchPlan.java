package com.vernflow.modules.workbench.model;

import java.util.List;

/**
 * Validated workbench plan graph used by both prepared and executed runs.
 *
 * @param title plan title
 * @param summary planner summary
 * @param toolChain display string for list views
 * @param inspectMode selected inspection mode
 * @param selectedNodeId default selected node id
 * @param mainPath highlighted main path node ids
 * @param nodes graph nodes
 * @param links graph links
 */
public record WorkbenchPlan(
        String title,
        String summary,
        String toolChain,
        String inspectMode,
        String selectedNodeId,
        List<String> mainPath,
        List<Node> nodes,
        List<Link> links
) {

    public WorkbenchPlan {
        mainPath = mainPath == null ? List.of() : List.copyOf(mainPath);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    public record Node(
            String id,
            String toolId,
            String type,
            String title,
            String detail,
            String positionClass,
            boolean branchNode,
            List<Check> checks,
            String payloadJson
    ) {
        public Node {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }

    public record Check(String tone, String label, String result) {
    }

    public record Link(String from, String to, boolean dashed) {
    }
}
