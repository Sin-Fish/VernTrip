package com.vernflow.modules.rag.service;

import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.assistant.service.WorkspaceDocumentScopeService;
import com.vernflow.modules.rag.dto.WorkspaceFileRelationGraphResponse;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkspaceFileRelationService {

    private static final Pattern CONCEPT_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,}|[\\u4e00-\\u9fff]{2,8}");
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
            "this", "that", "with", "from", "into", "about", "there", "their", "while", "where",
            "which", "what", "when", "your", "have", "has", "were", "been", "will", "would",
            "shall", "could", "should", "than", "then", "them", "they", "through", "such",
            "using", "used", "also", "over", "under", "more", "most", "very", "just",
            "does", "did", "each", "much", "many", "after", "before", "between"
    );

    private final WorkspaceDocumentScopeService workspaceDocumentScopeService;
    private final AiPaperClient aiPaperClient;

    public WorkspaceFileRelationService(
            WorkspaceDocumentScopeService workspaceDocumentScopeService,
            AiPaperClient aiPaperClient
    ) {
        this.workspaceDocumentScopeService = workspaceDocumentScopeService;
        this.aiPaperClient = aiPaperClient;
    }

    public WorkspaceFileRelationGraphResponse buildGraph(Long userId, String focusPath, int limit) {
        if (!StringUtils.hasText(focusPath)) {
            return new WorkspaceFileRelationGraphResponse(
                    null,
                    null,
                    "请先选择一个文件，再查看关联图。",
                    List.of(),
                    List.of()
            );
        }

        WorkspaceDocumentScopeService.ScopeExtractionResult scope = workspaceDocumentScopeService.collectScope(
                userId,
                true,
                List.of()
        );
        List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents = scope.readableDocuments();
        if (documents.isEmpty()) {
            return new WorkspaceFileRelationGraphResponse(
                    focusPath,
                    fileLabelFromPath(focusPath),
                    "当前工作区里还没有可分析的文件。",
                    List.of(),
                    List.of()
            );
        }

        List<DocumentFeature> features = buildDocumentFeatures(documents);
        DocumentFeature focus = features.stream()
                .filter(feature -> feature.path().equals(focusPath))
                .findFirst()
                .orElse(null);
        if (focus == null) {
            return new WorkspaceFileRelationGraphResponse(
                    focusPath,
                    fileLabelFromPath(focusPath),
                    "当前文件暂时还没有可展示的关联关系。",
                    List.of(),
                    List.of()
            );
        }

        List<DocumentRelation> relations = features.stream()
                .filter(feature -> !feature.path().equals(focus.path()))
                .map(candidate -> buildRelation(focus, candidate))
                .filter(DocumentRelation::isUsable)
                .sorted(Comparator.comparingDouble(DocumentRelation::score).reversed())
                .limit(Math.max(1, limit))
                .toList();

        List<WorkspaceFileRelationGraphResponse.Node> nodes = new ArrayList<>();
        nodes.add(new WorkspaceFileRelationGraphResponse.Node(
                focus.path(),
                focus.path(),
                focus.label(),
                focus.directoryLabel(),
                "focus",
                1.0,
                focus.concepts()
        ));

        List<WorkspaceFileRelationGraphResponse.Edge> edges = new ArrayList<>();
        for (DocumentRelation relation : relations) {
            nodes.add(new WorkspaceFileRelationGraphResponse.Node(
                    relation.target().path(),
                    relation.target().path(),
                    relation.target().label(),
                    relation.target().directoryLabel(),
                    "related",
                    relation.score(),
                    relation.target().concepts()
            ));
            edges.add(new WorkspaceFileRelationGraphResponse.Edge(
                    relation.source().path() + "->" + relation.target().path(),
                    relation.source().path(),
                    relation.target().path(),
                    relation.relationType(),
                    relation.score(),
                    relation.reasons()
            ));
        }

        String message = relations.isEmpty()
                ? "当前文件暂时没有明显关联，已展示该文件本身。"
                : "已展示当前文件最相关的文件关系。";
        return new WorkspaceFileRelationGraphResponse(
                focus.path(),
                focus.label(),
                message,
                nodes,
                edges
        );
    }

    private List<DocumentFeature> buildDocumentFeatures(
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents
    ) {
        List<String> snippets = documents.stream()
                .map(this::buildSnippet)
                .toList();
        Map<String, List<Float>> embeddings = embedSnippets(documents, snippets);

        return documents.stream()
                .map(document -> new DocumentFeature(
                        document.path(),
                        fileLabelFromPath(document.path()),
                        directoryLabelFromDocument(document),
                        extractConcepts(buildSnippet(document)),
                        embeddings.get(document.path())
                ))
                .toList();
    }

    private Map<String, List<Float>> embedSnippets(
            List<WorkspaceDocumentScopeService.ReadableWorkspaceDocument> documents,
            List<String> snippets
    ) {
        try {
            AiPaperClient.EmbedTextsResponse response = aiPaperClient.embedTexts(snippets, "passage");
            if (response.embeddings().size() != documents.size()) {
                return Map.of();
            }

            Map<String, List<Float>> vectors = new LinkedHashMap<>();
            for (int index = 0; index < documents.size(); index++) {
                vectors.put(documents.get(index).path(), response.embeddings().get(index).vector());
            }
            return vectors;
        } catch (BusinessException exception) {
            return Map.of();
        }
    }

    private String buildSnippet(WorkspaceDocumentScopeService.ReadableWorkspaceDocument document) {
        StringBuilder builder = new StringBuilder();
        builder.append(document.citeLabel()).append('\n');
        builder.append(document.sectionTitle()).append('\n');
        for (String chunk : document.chunks()) {
            if (builder.length() >= 2_400) {
                break;
            }
            builder.append(chunk).append('\n');
        }
        return builder.length() > 2_400 ? builder.substring(0, 2_400) : builder.toString();
    }

    private List<String> extractConcepts(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = CONCEPT_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = normalizeConcept(raw);
            if (!StringUtils.hasText(normalized) || ENGLISH_STOP_WORDS.contains(normalized)) {
                continue;
            }
            counts.merge(normalized, 1, Integer::sum);
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String normalizeConcept(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        boolean containsChinese = raw.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
        String trimmed = raw.trim();
        if (containsChinese) {
            return trimmed;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return normalized.length() < 3 ? null : normalized;
    }

    private DocumentRelation buildRelation(DocumentFeature focus, DocumentFeature candidate) {
        List<String> sharedConcepts = sharedConcepts(focus.concepts(), candidate.concepts());
        double lexicalScore = lexicalOverlap(focus.concepts(), candidate.concepts());
        double semanticScore = cosine(focus.embedding(), candidate.embedding());
        double sameDirectoryBonus = focus.directoryLabel().equals(candidate.directoryLabel()) ? 0.08 : 0.0;

        double score = semanticScore > 0
                ? (semanticScore * 0.7) + (lexicalScore * 0.25) + sameDirectoryBonus
                : lexicalScore + sameDirectoryBonus;
        score = Math.min(1.0, score);

        List<String> reasons = new ArrayList<>();
        if (!sharedConcepts.isEmpty()) {
            reasons.add("共享关键词：" + String.join("、", sharedConcepts.stream().limit(4).toList()));
        }
        if (semanticScore > 0) {
            reasons.add("语义相似度：" + formatScore(semanticScore));
        }
        if (sameDirectoryBonus > 0) {
            reasons.add("位于同一目录：" + focus.directoryLabel());
        }

        String relationType;
        if (semanticScore >= 0.7 && sharedConcepts.size() >= 2) {
            relationType = "semantic_similar";
        } else if (sharedConcepts.size() >= 2) {
            relationType = "shared_concepts";
        } else {
            relationType = "related";
        }

        return new DocumentRelation(focus, candidate, relationType, score, reasons);
    }

    private List<String> sharedConcepts(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return List.of();
        }

        Set<String> lookup = new LinkedHashSet<>(right);
        return left.stream()
                .filter(lookup::contains)
                .limit(5)
                .toList();
    }

    private double lexicalOverlap(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Set<String> leftSet = new LinkedHashSet<>(left);
        Set<String> rightSet = new LinkedHashSet<>(right);
        long intersection = leftSet.stream().filter(rightSet::contains).count();
        if (intersection == 0) {
            return 0.0;
        }

        int denominator = Math.max(leftSet.size(), rightSet.size());
        return denominator == 0 ? 0.0 : (double) intersection / denominator;
    }

    private double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }

        if (leftNorm <= 0 || rightNorm <= 0) {
            return 0.0;
        }

        return Math.max(0.0, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private String directoryLabelFromDocument(WorkspaceDocumentScopeService.ReadableWorkspaceDocument document) {
        return StringUtils.hasText(document.sectionTitle()) ? document.sectionTitle() : "workspace root";
    }

    private String fileLabelFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "未命名文件";
        }

        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }

    private record DocumentFeature(
            String path,
            String label,
            String directoryLabel,
            List<String> concepts,
            List<Float> embedding
    ) {
    }

    private record DocumentRelation(
            DocumentFeature source,
            DocumentFeature target,
            String relationType,
            double score,
            List<String> reasons
    ) {
        private boolean isUsable() {
            return score >= 0.18 || !reasons.isEmpty();
        }
    }
}
