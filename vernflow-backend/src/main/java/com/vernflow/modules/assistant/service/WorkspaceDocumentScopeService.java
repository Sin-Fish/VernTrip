package com.vernflow.modules.assistant.service;

import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiDocumentClient;
import com.vernflow.modules.rag.domain.WorkspaceChunkIndex;
import com.vernflow.modules.rag.repository.WorkspaceChunkIndexRepository;
import com.vernflow.modules.rag.repository.WorkspaceDocumentIndexRepository;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.service.UserWorkspaceRootService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves the current workspace scope into readable local documents.
 */
@Service
public class WorkspaceDocumentScopeService {

    private static final String ROOT_PATH = "workspace://";
    private static final int MAX_SCOPE_FILES = 48;
    private static final int MAX_TEXT_LENGTH = 18_000;
    private static final int MAX_CHUNK_LENGTH = 900;
    private static final int CHUNK_OVERLAP = 140;
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final String PDF_EXTENSION = "pdf";
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "md", "txt", "csv", "json", "yaml", "yml", "xml", "html", "htm",
            "java", "kt", "kts", "gradle", "properties", "js", "jsx", "ts", "tsx",
            "css", "scss", "sql", "py", "sh", "ps1", "vue"
    );
    private static final Set<String> OPEN_XML_EXTENSIONS = Set.of("docx", "pptx", "xlsx");

    private final UserWorkspaceRootService userWorkspaceRootService;
    private final AiDocumentClient aiDocumentClient;
    private final WorkspaceDocumentIndexRepository workspaceDocumentIndexRepository;
    private final WorkspaceChunkIndexRepository workspaceChunkIndexRepository;

    public WorkspaceDocumentScopeService(
            UserWorkspaceRootService userWorkspaceRootService,
            AiDocumentClient aiDocumentClient,
            WorkspaceDocumentIndexRepository workspaceDocumentIndexRepository,
            WorkspaceChunkIndexRepository workspaceChunkIndexRepository
    ) {
        this.userWorkspaceRootService = userWorkspaceRootService;
        this.aiDocumentClient = aiDocumentClient;
        this.workspaceDocumentIndexRepository = workspaceDocumentIndexRepository;
        this.workspaceChunkIndexRepository = workspaceChunkIndexRepository;
    }

    /**
     * Build a readable document scope from the current workspace selection.
     *
     * @param globalScope whether to traverse the full workspace
     * @param workingFiles selected working files
     * @return resolved readable and unreadable scope files
     */
    public ScopeExtractionResult collectScope(Long userId, boolean globalScope, List<WorkspaceItemRef> workingFiles) {
        Path workspaceRoot = ensureWorkspaceRoot(userWorkspaceRootService.resolveUserWorkspaceRoot(userId));
        List<Path> scopeFiles = globalScope
                ? listWorkspaceFiles(workspaceRoot)
                : listSelectedFiles(workspaceRoot, workingFiles);

        List<ReadableWorkspaceDocument> readableDocuments = new ArrayList<>();
        List<String> unreadableFiles = new ArrayList<>();

        for (Path file : scopeFiles) {
            String extension = extensionOf(file.getFileName().toString());
            if (PDF_EXTENSION.equals(extension)) {
                ReadableWorkspaceDocument pdfDocument = extractPdfDocument(userId, file, workspaceRoot);
                if (pdfDocument == null || pdfDocument.chunks().isEmpty()) {
                    unreadableFiles.add(workspaceRoot.relativize(file).toString().replace('\\', '/'));
                } else {
                    readableDocuments.add(pdfDocument);
                }
                continue;
            }

            ExtractedText extracted = extractText(file, workspaceRoot);
            if (!StringUtils.hasText(extracted.text())) {
                unreadableFiles.add(extracted.citeLabel());
                continue;
            }

            List<String> chunks = chunkText(extracted.text());
            if (chunks.isEmpty()) {
                unreadableFiles.add(extracted.citeLabel());
                continue;
            }

            readableDocuments.add(new ReadableWorkspaceDocument(
                    extracted.virtualPath(),
                    extracted.citeLabel(),
                    extracted.sectionTitle(),
                    extracted.sourceSignature(),
                    chunks
            ));
        }

        return new ScopeExtractionResult(readableDocuments, unreadableFiles, scopeFiles.size());
    }

    private ReadableWorkspaceDocument extractPdfDocument(Long userId, Path file, Path workspaceRoot) {
        String virtualPath = toVirtualPath(file, workspaceRoot);
        String citeLabel = workspaceRoot.relativize(file).toString().replace('\\', '/');
        String sectionTitle = sectionTitleFor(file, workspaceRoot);
        String sourceSignature = buildSourceSignature(file);

        List<String> cachedChunks = loadCachedChunks(userId, virtualPath, sourceSignature);
        if (!cachedChunks.isEmpty()) {
            return new ReadableWorkspaceDocument(
                    virtualPath,
                    citeLabel,
                    sectionTitle,
                    sourceSignature,
                    cachedChunks
            );
        }

        AiDocumentClient.ParseDocumentResponse response;
        try {
            response = aiDocumentClient.parsePdf(file.toAbsolutePath().toString());
        } catch (BusinessException exception) {
            return null;
        }
        List<String> chunks = response.chunks().stream()
                .map(AiDocumentClient.DocumentChunk::chunkText)
                .map(this::normalizeWhitespace)
                .filter(StringUtils::hasText)
                .toList();
        if (chunks.isEmpty()) {
            String fallbackText = normalizeWhitespace(
                    String.join(
                            "\n",
                            nullToEmpty(response.abstractText()),
                            nullToEmpty(response.aiSummary()),
                            nullToEmpty(response.summaryProblem()),
                            nullToEmpty(response.summaryMethod()),
                            nullToEmpty(response.summaryConclusion()),
                            nullToEmpty(response.summaryValue())
                    )
            );
            chunks = chunkText(fallbackText);
        }
        if (chunks.isEmpty()) {
            return null;
        }

        return new ReadableWorkspaceDocument(
                virtualPath,
                citeLabel,
                sectionTitle,
                sourceSignature,
                chunks
        );
    }

    private List<String> loadCachedChunks(Long userId, String virtualPath, String sourceSignature) {
        return workspaceDocumentIndexRepository.findByUserIdAndSourcePath(userId, virtualPath)
                .filter(document -> sourceSignature.equals(document.getSourceSignature()))
                .map(document -> workspaceChunkIndexRepository.findAllByDocumentIdOrderBySortIndexAsc(document.getId()))
                .stream()
                .flatMap(List::stream)
                .map(WorkspaceChunkIndex::getChunkText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<Path> listWorkspaceFiles(Path workspaceRoot) {
        try (Stream<Path> walk = Files.walk(workspaceRoot, 24)) {
            return walk
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> workspaceRoot.relativize(path).toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_SCOPE_FILES)
                    .toList();
        } catch (IOException exception) {
            throw new BusinessException("Failed to scan workspace files: " + exception.getMessage());
        }
    }

    private List<Path> listSelectedFiles(Path workspaceRoot, List<WorkspaceItemRef> workingFiles) {
        if (workingFiles == null || workingFiles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Path> collected = new LinkedHashSet<>();
        for (WorkspaceItemRef item : workingFiles) {
            Path path = resolveVirtualPath(item == null ? null : item.path(), workspaceRoot);
            if (path == null || !Files.exists(path)) {
                continue;
            }

            if (Files.isRegularFile(path)) {
                collected.add(path);
            } else if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path, 24)) {
                    walk.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(candidate -> workspaceRoot.relativize(candidate).toString().toLowerCase(Locale.ROOT)))
                            .forEach(collected::add);
                } catch (IOException exception) {
                    throw new BusinessException("Failed to inspect selected workspace folder: " + exception.getMessage());
                }
            }

            if (collected.size() >= MAX_SCOPE_FILES) {
                break;
            }
        }

        return collected.stream().limit(MAX_SCOPE_FILES).toList();
    }

    private ExtractedText extractText(Path file, Path workspaceRoot) {
        String extension = extensionOf(file.getFileName().toString());
        String virtualPath = toVirtualPath(file, workspaceRoot);
        String citeLabel = workspaceRoot.relativize(file).toString().replace('\\', '/');
        String sectionTitle = sectionTitleFor(file, workspaceRoot);
        String sourceSignature = buildSourceSignature(file);

        if (PLAIN_TEXT_EXTENSIONS.contains(extension)) {
            return new ExtractedText(
                    virtualPath,
                    citeLabel,
                    sectionTitle,
                    sourceSignature,
                    normalizeWhitespace(readPlainText(file))
            );
        }

        if (OPEN_XML_EXTENSIONS.contains(extension)) {
            return new ExtractedText(
                    virtualPath,
                    citeLabel,
                    sectionTitle,
                    sourceSignature,
                    normalizeWhitespace(readOpenXmlText(file))
            );
        }

        return new ExtractedText(virtualPath, citeLabel, sectionTitle, sourceSignature, null);
    }

    private String readPlainText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException utf8Exception) {
            try {
                return Files.readString(file, java.nio.charset.Charset.forName("GB18030"));
            } catch (IOException gbException) {
                return null;
            }
        }
    }

    private String readOpenXmlText(Path file) {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            StringBuilder builder = new StringBuilder();

            List<? extends ZipEntry> entries = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> isReadableOpenXmlEntry(entry.getName()))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();

            for (ZipEntry entry : entries) {
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    String xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    String text = stripXml(xml);
                    if (StringUtils.hasText(text)) {
                        builder.append(text).append(System.lineSeparator());
                    }
                }
            }

            return builder.toString();
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean isReadableOpenXmlEntry(String entryName) {
        return (entryName.startsWith("word/") || entryName.startsWith("ppt/slides/") || entryName.startsWith("xl/"))
                && entryName.endsWith(".xml");
    }

    private String stripXml(String xml) {
        String text = xml
                .replace("</w:p>", "\n")
                .replace("</a:p>", "\n")
                .replace("</row>", "\n")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        text = XML_TAG_PATTERN.matcher(text).replaceAll(" ");
        return text.replace("_x000D_", " ");
    }

    private List<String> chunkText(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalized = text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH)
                : text;
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, normalized.length());
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks.stream().filter(StringUtils::hasText).toList();
    }

    private String normalizeWhitespace(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sectionTitleFor(Path file, Path workspaceRoot) {
        Path relative = workspaceRoot.relativize(file).normalize();
        Path parent = relative.getParent();
        return parent == null ? "workspace root" : parent.toString().replace('\\', '/');
    }

    private String buildSourceSignature(Path file) {
        try {
            long size = Files.size(file);
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            return size + ":" + lastModified;
        } catch (IOException exception) {
            return "unknown";
        }
    }

    private Path ensureWorkspaceRoot(Path workspaceRoot) {
        try {
            Files.createDirectories(workspaceRoot);
            return workspaceRoot;
        } catch (IOException exception) {
            throw new BusinessException("Failed to initialize workspace root: " + exception.getMessage());
        }
    }

    private Path resolveVirtualPath(String virtualPath, Path workspaceRoot) {
        if (!StringUtils.hasText(virtualPath) || ROOT_PATH.equals(virtualPath)) {
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

    private String toVirtualPath(Path path, Path workspaceRoot) {
        Path relative = workspaceRoot.relativize(path.normalize().toAbsolutePath());
        String text = relative.toString().replace('\\', '/');
        return text.isBlank() ? ROOT_PATH : ROOT_PATH + text;
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ScopeExtractionResult(
            List<ReadableWorkspaceDocument> readableDocuments,
            List<String> unreadableFiles,
            long totalScopeFileCount
    ) {
    }

    public record ReadableWorkspaceDocument(
            String path,
            String citeLabel,
            String sectionTitle,
            String sourceSignature,
            List<String> chunks
    ) {
    }

    private record ExtractedText(
            String virtualPath,
            String citeLabel,
            String sectionTitle,
            String sourceSignature,
            String text
    ) {
    }
}
