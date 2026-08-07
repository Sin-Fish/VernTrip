package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_plan_item")
public class TravelPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "start_time", nullable = false, length = 8)
    private String startTime;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 40)
    private String tag;

    @Column(name = "estimated_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "source_name", length = 160)
    private String sourceName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TravelPlanItem() {
    }

    private TravelPlanItem(
            Long planId,
            Long resourceId,
            int dayNumber,
            int sequenceNo,
            String startTime,
            String label,
            String title,
            String description,
            String tag,
            BigDecimal estimatedCost,
            String sourceName
    ) {
        this.planId = planId;
        this.resourceId = resourceId;
        this.dayNumber = dayNumber;
        this.sequenceNo = sequenceNo;
        this.startTime = startTime;
        this.label = label;
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.estimatedCost = estimatedCost;
        this.sourceName = sourceName;
        this.createdAt = LocalDateTime.now();
    }

    public static TravelPlanItem create(
            Long planId,
            TourismResource resource,
            int dayNumber,
            int sequenceNo,
            String startTime,
            String label,
            String description,
            String tag,
            BigDecimal estimatedCost
    ) {
        return new TravelPlanItem(
                planId,
                resource.getId(),
                dayNumber,
                sequenceNo,
                startTime,
                label,
                resource.getName(),
                description,
                tag,
                estimatedCost,
                resource.getSourceName()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getLabel() {
        return label;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getSourceName() {
        return sourceName;
    }
}
