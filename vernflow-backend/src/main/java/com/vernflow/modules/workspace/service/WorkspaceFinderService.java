package com.vernflow.modules.workspace.service;

import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.workspace.dto.WorkspaceFileDescriptor;
import com.vernflow.modules.workspace.dto.WorkspaceFinderCreateRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderDeleteRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderListResponse;
import com.vernflow.modules.workspace.dto.WorkspaceFinderRenameRequest;
import com.vernflow.modules.workspace.dto.WorkspaceFinderSearchResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generic local workspace file manager used by VueFinder.
 */
@Service
public class WorkspaceFinderService {
    private static final String STORAGE = "workspace";
    private static final String ROOT_PATH = STORAGE + "://";

    private final WorkspaceFinderPathService workspaceFinderPathService;

    public WorkspaceFinderService(WorkspaceFinderPathService workspaceFinderPathService) {
        this.workspaceFinderPathService = workspaceFinderPathService;
    }

    /**
     * List files for a directory.
     *
     * @param rawPath requested virtual path
     * @return VueFinder-compatible listing
     */
    @Transactional(readOnly = true)
    public WorkspaceFinderListResponse list(Long userId, String rawPath) {
        String path = workspaceFinderPathService.normalizeDirectoryPath(rawPath);
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, path)
        );
        return directoryResponse(path, realDirectory);
    }

    /**
     * Search across the workspace root.
     *
     * @param filter search keyword
     * @return matching entries
     */
    @Transactional(readOnly = true)
    public WorkspaceFinderSearchResponse search(Long userId, String filter) {
        String normalizedFilter = StringUtils.hasText(filter)
                ? filter.trim().toLowerCase(Locale.ROOT)
                : "";

        Path workspaceRoot = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.workspaceRoot(userId)
        );
        try (Stream<Path> paths = Files.walk(workspaceRoot, 24)) {
            List<WorkspaceFinderListResponse.Entry> files = paths
                    .filter(path -> !path.equals(workspaceRoot))
                    .filter(path -> normalizedFilter.isBlank()
                            || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedFilter))
                    .sorted()
                    .map(this::toEntry)
                    .toList();
            return new WorkspaceFinderSearchResponse(files);
        } catch (IOException exception) {
            throw new BusinessException("Failed to search workspace files: " + exception.getMessage());
        }
    }

    /**
     * Upload a file into the current directory.
     *
     * @param rawPath target directory path
     * @param file uploaded file
     * @return refreshed directory payload
     */
    public WorkspaceFinderListResponse upload(Long userId, String rawPath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded file cannot be empty.");
        }

        String path = workspaceFinderPathService.normalizeDirectoryPath(rawPath);
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, path)
        );
        String fileName = workspaceFinderPathService.normalizeLeafName(file.getOriginalFilename());
        Path targetFile = workspaceFinderPathService.resolveUniqueChild(realDirectory, fileName);

        try {
            file.transferTo(targetFile);
        } catch (IOException exception) {
            throw new BusinessException("Failed to store uploaded file: " + exception.getMessage());
        }

        return directoryResponse(path, realDirectory);
    }

    /**
     * Create a new file.
     *
     * @param request create request
     * @return refreshed directory payload
     */
    public WorkspaceFinderListResponse createFile(Long userId, WorkspaceFinderCreateRequest request) {
        String directoryPath = workspaceFinderPathService.normalizeDirectoryPath(request.path());
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, directoryPath)
        );
        String fileName = workspaceFinderPathService.normalizeFileName(request.name());
        Path targetFile = realDirectory.resolve(fileName).normalize().toAbsolutePath();
        workspaceFinderPathService.ensureWithinWorkspaceRoot(targetFile);

        if (Files.exists(targetFile)) {
            throw new BusinessException("File already exists: " + fileName);
        }

        try {
            Files.createDirectories(targetFile.getParent());
            String initialContent = fileName.endsWith(".md")
                    ? "# " + stripExtension(fileName) + System.lineSeparator()
                    : "";
            Files.writeString(
                    targetFile,
                    initialContent,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new BusinessException("Failed to create file: " + exception.getMessage());
        }

        return directoryResponse(directoryPath, realDirectory);
    }

    /**
     * Create a new folder.
     *
     * @param request create request
     * @return refreshed directory payload
     */
    public WorkspaceFinderListResponse createFolder(Long userId, WorkspaceFinderCreateRequest request) {
        String directoryPath = workspaceFinderPathService.normalizeDirectoryPath(request.path());
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, directoryPath)
        );
        String folderName = workspaceFinderPathService.normalizeLeafName(request.name());
        Path targetDirectory = realDirectory.resolve(folderName).normalize().toAbsolutePath();
        workspaceFinderPathService.ensureWithinWorkspaceRoot(targetDirectory);

        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException exception) {
            throw new BusinessException("Failed to create folder: " + exception.getMessage());
        }

        return directoryResponse(directoryPath, realDirectory);
    }

    /**
     * Rename a file or folder.
     *
     * @param request rename request
     * @return refreshed directory payload
     */
    public WorkspaceFinderListResponse rename(Long userId, WorkspaceFinderRenameRequest request) {
        String directoryPath = workspaceFinderPathService.normalizeDirectoryPath(request.path());
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, directoryPath)
        );
        Path sourcePath = workspaceFinderPathService.resolvePath(userId, request.item());
        String newName = workspaceFinderPathService.normalizeLeafName(request.name());
        Path targetPath = sourcePath.resolveSibling(newName).normalize().toAbsolutePath();
        workspaceFinderPathService.ensureWithinWorkspaceRoot(targetPath);

        if (Files.exists(targetPath)) {
            throw new BusinessException("Target already exists: " + newName);
        }

        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new BusinessException("Failed to rename item: " + exception.getMessage());
        }

        return directoryResponse(directoryPath, realDirectory);
    }

    /**
     * Delete selected files or folders.
     *
     * @param request delete request
     * @return refreshed directory payload
     */
    public WorkspaceFinderListResponse delete(Long userId, WorkspaceFinderDeleteRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("Please select at least one item.");
        }

        String directoryPath = workspaceFinderPathService.normalizeDirectoryPath(request.path());
        Path realDirectory = workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(userId, directoryPath)
        );

        for (WorkspaceFinderDeleteRequest.ItemRef item : request.items()) {
            deleteRecursively(workspaceFinderPathService.resolvePath(userId, item.path()));
        }

        return directoryResponse(directoryPath, realDirectory);
    }

    /**
     * Expand selected workspace references into concrete files. Folder references are traversed recursively.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceItemRef> collectFiles(Long userId, List<WorkspaceItemRef> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        List<WorkspaceItemRef> files = new ArrayList<>();
        for (WorkspaceItemRef item : items) {
            if (item == null || !StringUtils.hasText(item.path())) {
                continue;
            }
            collectFilesFromPath(userId, item.path(), files, seenPaths);
        }
        return List.copyOf(files);
    }

    /**
     * Ensure a directory exists, creating missing parents as needed.
     */
    public void ensureDirectory(Long userId, String rawPath) {
        workspaceFinderPathService.ensureWorkspaceDirectory(
                workspaceFinderPathService.resolveDirectory(
                        userId,
                        workspaceFinderPathService.normalizeDirectoryPath(rawPath)
                )
        );
    }

    /**
     * Create or overwrite a UTF-8 text file inside the workspace.
     */
    public WorkspaceItemRef writeTextFile(Long userId, String rawFilePath, String content) {
        Path targetFile = workspaceFinderPathService.resolvePath(userId, rawFilePath);
        workspaceFinderPathService.ensureWithinWorkspaceRoot(targetFile);

        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    targetFile,
                    content == null ? "" : content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new BusinessException("Failed to write workspace file: " + exception.getMessage());
        }

        return new WorkspaceItemRef(
                workspaceFinderPathService.toVirtualPath(targetFile),
                targetFile.getFileName().toString(),
                "file"
        );
    }

    /**
     * Move one file to an explicit target file path.
     */
    public WorkspaceItemRef moveFileToPath(Long userId, String itemPath, String targetPath) {
        Path sourcePath = workspaceFinderPathService.resolvePath(userId, itemPath);
        if (!Files.exists(sourcePath) || Files.isDirectory(sourcePath)) {
            throw new BusinessException("Source file not found: " + itemPath);
        }

        Path resolvedTargetPath = workspaceFinderPathService.resolvePath(userId, targetPath);
        workspaceFinderPathService.ensureWithinWorkspaceRoot(resolvedTargetPath);

        if (sourcePath.equals(resolvedTargetPath)) {
            return new WorkspaceItemRef(itemPath, sourcePath.getFileName().toString(), "file");
        }
        if (Files.exists(resolvedTargetPath)) {
            throw new BusinessException("Target file already exists: " + targetPath);
        }

        try {
            Files.createDirectories(resolvedTargetPath.getParent());
            Files.move(sourcePath, resolvedTargetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new BusinessException("Failed to move file: " + exception.getMessage());
        }

        return new WorkspaceItemRef(
                workspaceFinderPathService.toVirtualPath(resolvedTargetPath),
                resolvedTargetPath.getFileName().toString(),
                "file"
        );
    }

    /**
     * Delete directories that are empty. The cleanup will walk upwards until a non-empty directory is reached.
     */
    public List<String> deleteEmptyDirectories(Long userId, List<String> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }

        Path workspaceRoot = workspaceFinderPathService.workspaceRoot(userId);
        LinkedHashSet<String> deleted = new LinkedHashSet<>();
        for (String rawPath : rawPaths) {
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }

            Path current = workspaceFinderPathService.resolveDirectory(
                    userId,
                    workspaceFinderPathService.normalizeDirectoryPath(rawPath)
            );
            while (!current.equals(workspaceRoot) && Files.exists(current) && Files.isDirectory(current) && isDirectoryEmpty(current)) {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    throw new BusinessException("Failed to delete empty folder: " + exception.getMessage());
                }
                deleted.add(workspaceFinderPathService.toVirtualPath(current));
                current = current.getParent();
            }
        }
        return List.copyOf(deleted);
    }

    /**
     * Delete every empty directory found inside the provided directory roots, excluding preserved subtrees.
     */
    public List<String> deleteEmptyDirectoriesWithin(Long userId, List<String> rawPaths, List<String> preservedPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }

        Path workspaceRoot = workspaceFinderPathService.workspaceRoot(userId);
        List<Path> preservedDirectories = ensureArray(preservedPaths).stream()
                .filter(StringUtils::hasText)
                .map(workspaceFinderPathService::normalizeDirectoryPath)
                .map(path -> workspaceFinderPathService.resolveDirectory(userId, path))
                .map(Path::normalize)
                .toList();

        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        for (String rawPath : rawPaths) {
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }

            Path root = workspaceFinderPathService.resolveDirectory(
                    userId,
                    workspaceFinderPathService.normalizeDirectoryPath(rawPath)
            );
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue;
            }

            try (Stream<Path> walk = Files.walk(root, 24)) {
                walk.filter(Files::isDirectory)
                        .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                        .forEach(directories::add);
            } catch (IOException exception) {
                throw new BusinessException("Failed to inspect workspace folders: " + exception.getMessage());
            }
        }

        LinkedHashSet<String> deleted = new LinkedHashSet<>();
        for (Path directory : directories) {
            if (directory.equals(workspaceRoot) || isPreservedDirectory(directory, preservedDirectories)) {
                continue;
            }
            if (!Files.exists(directory) || !Files.isDirectory(directory) || !isDirectoryEmpty(directory)) {
                continue;
            }

            try {
                Files.deleteIfExists(directory);
            } catch (IOException exception) {
                throw new BusinessException("Failed to delete empty folder: " + exception.getMessage());
            }
            deleted.add(workspaceFinderPathService.toVirtualPath(directory));
        }

        return List.copyOf(deleted);
    }

    /**
     * Check whether the workspace path currently exists.
     */
    @Transactional(readOnly = true)
    public boolean exists(Long userId, String rawPath) {
        return Files.exists(workspaceFinderPathService.resolvePath(userId, rawPath));
    }

    /**
     * Resolve a file for preview or download.
     *
     * @param rawPath target file path
     * @return descriptor
     */
    @Transactional(readOnly = true)
    public WorkspaceFileDescriptor resolveFile(Long userId, String rawPath) {
        Path filePath = workspaceFinderPathService.resolvePath(userId, rawPath);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new BusinessException("Workspace file not found: " + rawPath);
        }

        return new WorkspaceFileDescriptor(
                filePath.getFileName().toString(),
                filePath,
                probeMediaType(filePath)
        );
    }

    private WorkspaceFinderListResponse directoryResponse(String virtualPath, Path realDirectory) {
        return new WorkspaceFinderListResponse(
                List.of(STORAGE),
                virtualPath,
                listEntries(realDirectory),
                false
        );
    }

    private List<WorkspaceFinderListResponse.Entry> listEntries(Path realDirectory) {
        try (Stream<Path> children = Files.list(realDirectory)) {
            return children
                    .sorted(entryComparator())
                    .map(this::toEntry)
                    .toList();
        } catch (IOException exception) {
            throw new BusinessException("Failed to read workspace directory: " + exception.getMessage());
        }
    }

    private WorkspaceFinderListResponse.Entry toEntry(Path realPath) {
        Path normalized = realPath.normalize().toAbsolutePath();
        Long userId = workspaceFinderPathService.userIdForPath(normalized);
        Path userWorkspaceRoot = workspaceFinderPathService.workspaceRoot(userId);
        Path parentPath = normalized.getParent();
        String dir = parentPath == null || parentPath.equals(userWorkspaceRoot)
                ? ROOT_PATH
                : workspaceFinderPathService.toVirtualPath(parentPath);
        boolean directory = Files.isDirectory(normalized);
        String basename = normalized.getFileName().toString();

        return new WorkspaceFinderListResponse.Entry(
                dir,
                basename,
                directory ? "" : extensionOf(basename),
                workspaceFinderPathService.toVirtualPath(normalized),
                STORAGE,
                directory ? "dir" : "file",
                directory ? null : fileSize(normalized),
                lastModified(normalized),
                directory ? null : probeMediaType(normalized).toString(),
                false,
                "private",
                null
        );
    }

    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException runtimeException) {
            Throwable cause = runtimeException.getCause();
            if (cause instanceof IOException ioException) {
                throw new BusinessException("Failed to delete item: " + ioException.getMessage());
            }
            throw runtimeException;
        } catch (IOException exception) {
            throw new BusinessException("Failed to delete item: " + exception.getMessage());
        }
    }

    private Comparator<Path> entryComparator() {
        return Comparator
                .comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT));
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

    private Long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long lastModified(Path filePath) {
        try {
            return Files.getLastModifiedTime(filePath).toMillis();
        } catch (IOException exception) {
            return null;
        }
    }

    private MediaType probeMediaType(Path filePath) {
        try {
            String mimeType = Files.probeContentType(filePath);
            return StringUtils.hasText(mimeType)
                    ? MediaType.parseMediaType(mimeType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IOException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private void collectFilesFromPath(
            Long userId,
            String rawPath,
            List<WorkspaceItemRef> files,
            Set<String> seenPaths
    ) {
        Path path = workspaceFinderPathService.resolvePath(userId, rawPath);
        if (!Files.exists(path)) {
            return;
        }

        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path, 24)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(filePath -> addWorkspaceFile(filePath, files, seenPaths));
            } catch (IOException exception) {
                throw new BusinessException("Failed to read workspace folder: " + exception.getMessage());
            }
            return;
        }

        addWorkspaceFile(path, files, seenPaths);
    }

    private void addWorkspaceFile(Path filePath, List<WorkspaceItemRef> files, Set<String> seenPaths) {
        String virtualPath = workspaceFinderPathService.toVirtualPath(filePath);
        if (seenPaths.add(virtualPath)) {
            files.add(new WorkspaceItemRef(virtualPath, filePath.getFileName().toString(), "file"));
        }
    }

    private boolean isDirectoryEmpty(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        } catch (IOException exception) {
            throw new BusinessException("Failed to inspect directory: " + exception.getMessage());
        }
    }

    private List<String> ensureArray(List<String> items) {
        return items == null ? List.of() : items;
    }

    private boolean isPreservedDirectory(Path directory, List<Path> preservedDirectories) {
        return preservedDirectories.stream().anyMatch(directory::startsWith);
    }
}
