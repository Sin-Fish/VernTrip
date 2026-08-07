package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_plan")
public class TravelPlan {

    public enum Status {
        DRAFT,
        SAVED,
        ADOPTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trip_request_id", nullable = false)
    private Long tripRequestId;

    @Column(name = "parent_plan_id")
    private Long parentPlanId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(name = "estimated_budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(name = "generation_engine", nullable = false, length = 32)
    private String generationEngine;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TravelPlan() {
    }

    private TravelPlan(
            Long userId,
            Long tripRequestId,
            Long parentPlanId,
            int version,
            String title,
            String summary,
            int matchScore,
            BigDecimal estimatedBudget,
            String generationEngine
    ) {
        this.userId = userId;
        this.tripRequestId = tripRequestId;
        this.parentPlanId = parentPlanId;
        this.version = version;
        this.title = title;
        this.summary = summary;
        this.status = Status.DRAFT;
        this.matchScore = matchScore;
        this.estimatedBudget = estimatedBudget;
        this.generationEngine = generationEngine;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TravelPlan create(
            Long userId,
            Long tripRequestId,
            Long parentPlanId,
            int version,
            String title,
            String summary,
            int matchScore,
            BigDecimal estimatedBudget,
            String generationEngine
    ) {
        return new TravelPlan(
                userId,
                tripRequestId,
                parentPlanId,
                version,
                title,
                summary,
                matchScore,
                estimatedBudget,
                generationEngine
        );
    }

    public void markSaved() {
        this.status = Status.SAVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDraft() {
        this.status = Status.DRAFT;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTripRequestId() {
        return tripRequestId;
    }

    public Long getParentPlanId() {
        return parentPlanId;
    }

    public int getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Status getStatus() {
        return status;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public BigDecimal getEstimatedBudget() {
        return estimatedBudget;
    }

    public String getGenerationEngine() {
        return generationEngine;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
