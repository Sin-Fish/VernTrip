package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.util.List;

public record PaperGraphResponse(Long centerPaperId, List<Node> nodes, List<Edge> edges) {

    public record Node(Long paperId, String title, Integer publicationYear, boolean highlighted) {
    }

    public record Edge(Long sourcePaperId, Long targetPaperId, RelationType relationType, Double confidence) {
    }
}

