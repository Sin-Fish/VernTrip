package com.vernflow.modules.workbench.model;

import java.util.List;

/**
 * Deterministic workspace organization plan reused by decision and execution nodes.
 *
 * @param baseDirectoryPath common base directory for the selected files
 * @param organizerRootPath target root directory created for organized materials
 * @param folders category folders to create
 * @param moves file move operations
 * @param cleanupCandidates source directories that may become empty after moves
 * @param scopeRoots directory roots covered by this organization task
 * @param generateExecutionReport whether an execution report should be written after organization
 * @param reportDirectoryPath target directory used for execution reports
 * @param reportFilePath target file path used for the execution report
 * @param warnings planning warnings shown to the operator
 */
public record WorkspaceOrganizationPlan(
        String baseDirectoryPath,
        String organizerRootPath,
        List<CategoryFolder> folders,
        List<MoveOperation> moves,
        List<String> cleanupCandidates,
        List<String> scopeRoots,
        boolean generateExecutionReport,
        String reportDirectoryPath,
        String reportFilePath,
        List<String> warnings
) {

    public WorkspaceOrganizationPlan {
        folders = folders == null ? List.of() : List.copyOf(folders);
        moves = moves == null ? List.of() : List.copyOf(moves);
        cleanupCandidates = cleanupCandidates == null ? List.of() : List.copyOf(cleanupCandidates);
        scopeRoots = scopeRoots == null ? List.of() : List.copyOf(scopeRoots);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record CategoryFolder(String name, String path, int fileCount) {
    }

    public record MoveOperation(
            String sourcePath,
            String sourceLabel,
            String categoryName,
            String targetDirectoryPath,
            String targetPath
    ) {
    }
}
