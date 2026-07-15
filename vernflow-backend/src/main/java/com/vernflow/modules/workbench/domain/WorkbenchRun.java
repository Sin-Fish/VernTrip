package com.vernflow.modules.workbench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Persisted workspace-first workbench run.
 */
@Entity
@Table(name = "workbench_run")
public class WorkbenchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "global_scope", nullable = false)
    private boolean globalScope;

    @Column(name = "inspect_mode", nullable = false, length = 32)
    private String inspectMode;

    @Column(name = "tool_chain", nullable = false, length = 255)
    private String toolChain;

    @Column(name = "plan_summary", nullable = false, columnDefinition = "TEXT")
    private String planSummary;

    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "runtime_json", nullable = false, columnDefinition = "TEXT")
    private String runtimeJson;

    @Column(name = "history_json", nullable = false, columnDefinition = "TEXT")
    private String historyJson;

    @Column(name = "rag_profile_id", nullable = false, length = 64)
    private String ragProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkbenchRunStatus status;

    @Column(name = "working_files_json", nullable = false, columnDefinition = "TEXT")
    private String workingFilesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected WorkbenchRun() {
    }

    private WorkbenchRun(
            Long userId,
            String name,
            String prompt,
            boolean globalScope,
            String inspectMode,
            String toolChain,
            String planSummary,
            String planJson,
            String runtimeJson,
            String historyJson,
            String ragProfileId,
            String workingFilesJson,
            WorkbenchRunStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.userId = userId;
        this.name = name;
        this.prompt = prompt;
        this.globalScope = globalScope;
        this.inspectMode = inspectMode;
        this.toolChain = toolChain;
        this.planSummary = planSummary;
        this.planJson = planJson;
        this.runtimeJson = runtimeJson;
        this.historyJson = historyJson;
        this.ragProfileId = ragProfileId;
        this.workingFilesJson = workingFilesJson;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
        if (status == WorkbenchRunStatus.RUNNING || status == WorkbenchRunStatus.COMPLETED) {
            this.startedAt = now;
        }
        if (status == WorkbenchRunStatus.COMPLETED) {
            this.finishedAt = now;
        }
    }

    public static WorkbenchRun create(
            Long userId,
            String name,
            String prompt,
            boolean globalScope,
            String inspectMode,
            String toolChain,
            String planSummary,
            String planJson,
            String runtimeJson,
            String historyJson,
            String ragProfileId,
            String workingFilesJson,
            WorkbenchRunStatus status
    ) {
        return new WorkbenchRun(
                userId,
                name,
                prompt,
                globalScope,
                inspectMode,
                toolChain,
                planSummary,
                planJson,
                runtimeJson,
                historyJson,
                ragProfileId,
                workingFilesJson,
                status
        );
    }

    public void revise(
            String name,
            String prompt,
            boolean globalScope,
            String inspectMode,
            String toolChain,
            String planSummary,
            String planJson,
            String runtimeJson,
            String historyJson,
            String ragProfileId,
            String workingFilesJson,
            WorkbenchRunStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.name = name;
        this.prompt = prompt;
        this.globalScope = globalScope;
        this.inspectMode = inspectMode;
        this.toolChain = toolChain;
        this.planSummary = planSummary;
        this.planJson = planJson;
        this.runtimeJson = runtimeJson;
        this.historyJson = historyJson;
        this.ragProfileId = ragProfileId;
        this.workingFilesJson = workingFilesJson;
        this.status = status;
        this.updatedAt = now;
        if (status == WorkbenchRunStatus.RUNNING) {
            if (this.startedAt == null) {
                this.startedAt = now;
            }
            this.finishedAt = null;
        } else if (status == WorkbenchRunStatus.COMPLETED) {
            if (this.startedAt == null) {
                this.startedAt = now;
            }
            this.finishedAt = now;
        } else {
            this.startedAt = null;
            this.finishedAt = null;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean isGlobalScope() {
        return globalScope;
    }

    public String getInspectMode() {
        return inspectMode;
    }

    public String getToolChain() {
        return toolChain;
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public String getPlanJson() {
        return planJson;
    }

    public String getRuntimeJson() {
        return runtimeJson;
    }

    public String getHistoryJson() {
        return historyJson;
    }

    public String getRagProfileId() {
        return ragProfileId;
    }

    public WorkbenchRunStatus getStatus() {
        return status;
    }

    public String getWorkingFilesJson() {
        return workingFilesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
