package com.vernflow.modules.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Auditable admin action entry for governance-oriented operations.
 */
@Entity
@Table(name = "admin_operation_log")
public class AdminOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_path", columnDefinition = "TEXT")
    private String targetPath;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminOperationLog() {
    }

    private AdminOperationLog(
            String actor,
            String action,
            String targetType,
            String targetPath,
            String detail
    ) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetPath = targetPath;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public static AdminOperationLog create(
            String actor,
            String action,
            String targetType,
            String targetPath,
            String detail
    ) {
        return new AdminOperationLog(actor, action, targetType, targetPath, detail);
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
