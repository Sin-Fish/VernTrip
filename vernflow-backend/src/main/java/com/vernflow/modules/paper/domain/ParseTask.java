package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import com.vernflow.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "parse_task")
public class ParseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected ParseTask() {
    }

    private ParseTask(Paper paper, long queueOrder) {
        this.paper = paper;
        this.taskType = TaskType.PAPER_PARSE;
        this.status = TaskStatus.PENDING;
        this.queueOrder = queueOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static ParseTask create(Paper paper) {
        return new ParseTask(paper, System.currentTimeMillis());
    }

    public static ParseTask create(Paper paper, long queueOrder) {
        return new ParseTask(paper, queueOrder);
    }

    public void markProcessing() {
        this.status = TaskStatus.PROCESSING;
        this.errorMessage = null;
        this.startedAt = LocalDateTime.now();
        this.finishedAt = null;
        touch();
    }

    public void markSuccess() {
        this.status = TaskStatus.SUCCESS;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    public void moveToFront(long queueOrder) {
        ensureStatus(TaskStatus.PENDING, "只有排队中的任务可以前置");
        this.queueOrder = queueOrder;
        touch();
    }

    public void pause() {
        ensureStatus(TaskStatus.PENDING, "只有排队中的任务可以暂停");
        this.status = TaskStatus.PAUSED;
        touch();
    }

    public void resume() {
        ensureStatus(TaskStatus.PAUSED, "只有暂停中的任务可以恢复");
        this.status = TaskStatus.PENDING;
        touch();
    }

    public void resetForRetry(long queueOrder) {
        if (this.status == TaskStatus.PROCESSING) {
            throw new BusinessException("处理中任务不能直接重试");
        }
        this.status = TaskStatus.PENDING;
        this.queueOrder = queueOrder;
        this.errorMessage = null;
        this.startedAt = null;
        this.finishedAt = null;
        touch();
    }

    public Long getId() {
        return id;
    }

    public Paper getPaper() {
        return paper;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public long getQueueOrder() {
        return queueOrder;
    }

    public String getErrorMessage() {
        return errorMessage;
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

    private void ensureStatus(TaskStatus expectedStatus, String message) {
        if (this.status != expectedStatus) {
            throw new BusinessException(message);
        }
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

