package com.vernflow.modules.rag.dto;

import java.util.List;

public record WorkspaceFileRelationGraphResponse(
        String focusPath,
        String focusLabel,
        String statusMessage,
        List<Node> nodes,
        List<Edge> edges
) {

    public WorkspaceFileRelationGraphResponse {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public record Node(
            String id,
            String path,
            String label,
            String directoryLabel,
            String role,
            double score,
            List<String> concepts
    ) {
        public Node {
            concepts = concepts == null ? List.of() : List.copyOf(concepts);
        }
    }

    public record Edge(
            String id,
            String source,
            String target,
            String relationType,
            double score,
            List<String> reasons
    ) {
        public Edge {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}
