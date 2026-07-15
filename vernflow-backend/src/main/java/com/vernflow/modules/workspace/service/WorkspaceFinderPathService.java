package com.vernflow.modules.workspace.service;

import com.vernflow.common.exception.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class WorkspaceFinderPathService {
    private static final String STORAGE = "workspace";
    private static final String ROOT_PATH = STORAGE + "://";

    private final UserWorkspaceRootService userWorkspaceRootService;

    public WorkspaceFinderPathService(UserWorkspaceRootService userWorkspaceRootService) {
        this.userWorkspaceRootService = userWorkspaceRootService;
    }

    public String normalizeDirectoryPath(String rawPath) {
        String path = StringUtils.hasText(rawPath) ? rawPath.trim() : ROOT_PATH;
        if (!path.startsWith(ROOT_PATH)) {
            throw new BusinessException("Unsupported workspace path: " + path);
        }
        if (path.length() > ROOT_PATH.length() && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    public Path resolveDirectory(Long userId, String virtualPath) {
        Path path = resolvePath(userId, virtualPath);
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new BusinessException("Target path is not a directory: " + virtualPath);
        }
        return path;
    }

    public Path resolvePath(Long userId, String virtualPath) {
        String path = normalizeDirectoryPath(virtualPath);
        String relative = path.equals(ROOT_PATH) ? "" : path.substring(ROOT_PATH.length());
        Path resolved = workspaceRoot(userId)
                .resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize()
                .toAbsolutePath();
        ensureWithinWorkspaceRoot(resolved);
        return resolved;
    }

    public String toVirtualPath(Path realPath) {
        Path normalized = realPath.normalize().toAbsolutePath();
        Path relative = workspaceRoot(userIdForPath(normalized)).relativize(normalized);
        String relativeText = relative.toString().replace('\\', '/');
        return relativeText.isBlank() ? ROOT_PATH : ROOT_PATH + relativeText;
    }

    public Path workspaceRoot(Long userId) {
        return userWorkspaceRootService.resolveUserWorkspaceRoot(userId).normalize().toAbsolutePath();
    }

    public Path ensureWorkspaceDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (Exception exception) {
            throw new BusinessException("Failed to initialize workspace directory: " + exception.getMessage());
        }
    }

    public void ensureWithinWorkspaceRoot(Path path) {
        Path root = userWorkspaceRootService.globalWorkspaceRoot();
        if (!path.startsWith(root)) {
            throw new BusinessException("Workspace path escapes the workspace root.");
        }
    }

    public Long userIdForPath(Path path) {
        Path globalRoot = userWorkspaceRootService.globalWorkspaceRoot();
        Path normalized = path.normalize().toAbsolutePath();
        if (!normalized.startsWith(globalRoot.resolve("users"))) {
            throw new BusinessException("Workspace path is outside managed user roots.");
        }

        Path relative = globalRoot.relativize(normalized);
        String segment = relative.getNameCount() >= 2 ? relative.getName(1).toString() : "";
        if (!segment.startsWith("u-")) {
            throw new BusinessException("Workspace path is missing a user directory.");
        }

        int secondDash = segment.indexOf('-', 2);
        String idText = secondDash > 0 ? segment.substring(2, secondDash) : segment.substring(2);
        try {
            return Long.parseLong(idText);
        } catch (NumberFormatException exception) {
            throw new BusinessException("Workspace path contains an invalid user directory.");
        }
    }

    public String normalizeLeafName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException("Name cannot be empty.");
        }

        String name = Path.of(rawName).getFileName().toString().trim();
        if (!StringUtils.hasText(name) || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException("Illegal name: " + rawName);
        }
        return name;
    }

    public String normalizeFileName(String rawName) {
        String normalized = normalizeLeafName(rawName);
        return normalized.contains(".") ? normalized : normalized + ".md";
    }

    public Path resolveUniqueChild(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName).normalize().toAbsolutePath();
        ensureWithinWorkspaceRoot(candidate);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String extension = extensionOf(fileName);
        String basename = stripExtension(fileName);
        int counter = 1;
        while (true) {
            String nextName = extension.isBlank()
                    ? basename + "-" + counter
                    : basename + "-" + counter + "." + extension;
            Path nextCandidate = directory.resolve(nextName).normalize().toAbsolutePath();
            ensureWithinWorkspaceRoot(nextCandidate);
            if (!Files.exists(nextCandidate)) {
                return nextCandidate;
            }
            counter++;
        }
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}
