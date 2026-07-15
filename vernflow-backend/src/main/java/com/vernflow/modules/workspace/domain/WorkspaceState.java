package com.vernflow.modules.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Per-user entity storing workspace-shell UI state.
 */
@Entity
@Table(name = "workspace_state")
public class WorkspaceState {

    @Id
    private Long id;

    @Column(name = "working_files_json", nullable = false, columnDefinition = "TEXT")
    private String workingFilesJson;

    @Column(name = "global_scope", nullable = false)
    private boolean globalScope;

    @Column(name = "selected_tree_path", length = 1024)
    private String selectedTreePath;

    @Column(name = "home_task_draft", columnDefinition = "TEXT")
    private String homeTaskDraft;

    @Column(name = "workbench_prompt", columnDefinition = "TEXT")
    private String workbenchPrompt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkspaceState() {
    }

    private WorkspaceState(
            Long userId,
            String workingFilesJson,
            boolean globalScope,
            String selectedTreePath,
            String homeTaskDraft,
            String workbenchPrompt
    ) {
        this.id = userId;
        this.workingFilesJson = workingFilesJson;
        this.globalScope = globalScope;
        this.selectedTreePath = selectedTreePath;
        this.homeTaskDraft = homeTaskDraft;
        this.workbenchPrompt = workbenchPrompt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static WorkspaceState createDefault(
            Long userId,
            String workingFilesJson,
            String selectedTreePath,
            String homeTaskDraft,
            String workbenchPrompt
    ) {
        return new WorkspaceState(userId, workingFilesJson, false, selectedTreePath, homeTaskDraft, workbenchPrompt);
    }

    public void update(
            String workingFilesJson,
            Boolean globalScope,
            String selectedTreePath,
            String homeTaskDraft,
            String workbenchPrompt
    ) {
        if (workingFilesJson != null) {
            this.workingFilesJson = workingFilesJson;
        }
        if (globalScope != null) {
            this.globalScope = globalScope;
        }
        this.selectedTreePath = selectedTreePath;
        this.homeTaskDraft = homeTaskDraft;
        this.workbenchPrompt = workbenchPrompt;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getWorkingFilesJson() {
        return workingFilesJson;
    }

    public boolean isGlobalScope() {
        return globalScope;
    }

    public String getSelectedTreePath() {
        return selectedTreePath;
    }

    public String getHomeTaskDraft() {
        return homeTaskDraft;
    }

    public String getWorkbenchPrompt() {
        return workbenchPrompt;
    }
}
