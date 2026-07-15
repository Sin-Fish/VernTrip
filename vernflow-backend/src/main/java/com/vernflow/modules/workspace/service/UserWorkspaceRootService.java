package com.vernflow.modules.workspace.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.repository.AppUserRepository;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class UserWorkspaceRootService {

    private final StorageProperties storageProperties;
    private final AppUserRepository appUserRepository;

    public UserWorkspaceRootService(
            StorageProperties storageProperties,
            AppUserRepository appUserRepository
    ) {
        this.storageProperties = storageProperties;
        this.appUserRepository = appUserRepository;
    }

    public Path globalWorkspaceRoot() {
        Path root = storageProperties.workspaceRootDir().normalize().toAbsolutePath();
        try {
            Files.createDirectories(root);
            return root;
        } catch (IOException exception) {
            throw new BusinessException("Failed to initialize global workspace root: " + exception.getMessage());
        }
    }

    public Path resolveUserWorkspaceRoot(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User was not found: " + userId));
        Path baseRoot = globalWorkspaceRoot();
        Path targetRoot = baseRoot
                .resolve("users")
                .resolve("u-" + user.getId() + "-" + sanitizeUserSegment(user.getUsername()))
                .normalize()
                .toAbsolutePath();

        migrateLegacyRootIfNeeded(user.getId(), baseRoot, targetRoot);

        try {
            Files.createDirectories(targetRoot);
            return targetRoot;
        } catch (IOException exception) {
            throw new BusinessException("Failed to initialize user workspace root: " + exception.getMessage());
        }
    }

    private void migrateLegacyRootIfNeeded(Long userId, Path baseRoot, Path targetRoot) {
        if (!Long.valueOf(1L).equals(userId) || Files.exists(targetRoot)) {
            return;
        }

        try (Stream<Path> children = Files.list(baseRoot)) {
            List<Path> legacyItems = children
                    .filter(path -> !path.getFileName().toString().equals("users"))
                    .toList();

            if (legacyItems.isEmpty()) {
                return;
            }

            Files.createDirectories(targetRoot);
            for (Path item : legacyItems) {
                Files.move(
                        item,
                        targetRoot.resolve(item.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw new BusinessException("Failed to migrate legacy workspace files: " + exception.getMessage());
        }
    }

    private String sanitizeUserSegment(String username) {
        String sanitized = username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        return sanitized.isBlank() ? "user" : sanitized;
    }
}
