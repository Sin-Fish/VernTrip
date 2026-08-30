package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tourism_product")
public class TourismProduct {

    public enum Status {
        DRAFT,
        PUBLISHED,
        INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_user_id", nullable = false)
    private Long merchantUserId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "target_audience", nullable = false, length = 120)
    private String targetAudience;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "highlights_json", nullable = false, columnDefinition = "TEXT")
    private String highlightsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tourism_product_resource",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
    @OrderBy("id ASC")
    private Set<TourismResource> resources = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TourismProduct() {
    }

    private TourismProduct(
            Long merchantUserId,
            String name,
            String targetAudience,
            int durationDays,
            BigDecimal price,
            String description,
            String highlightsJson,
            Collection<TourismResource> resources
    ) {
        this.merchantUserId = merchantUserId;
        updateDetails(name, targetAudience, durationDays, price, description, highlightsJson, resources);
        this.status = Status.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TourismProduct draft(
            Long merchantUserId,
            String name,
            String targetAudience,
            int durationDays,
            BigDecimal price,
            String description,
            String highlightsJson,
            Collection<TourismResource> resources
    ) {
        return new TourismProduct(
                merchantUserId,
                name,
                targetAudience,
                durationDays,
                price,
                description,
                highlightsJson,
                resources
        );
    }

    public void updateDetails(
            String name,
            String targetAudience,
            int durationDays,
            BigDecimal price,
            String description,
            String highlightsJson,
            Collection<TourismResource> resources
    ) {
        this.name = name;
        this.targetAudience = targetAudience;
        this.durationDays = durationDays;
        this.price = price;
        this.description = description;
        this.highlightsJson = highlightsJson;
        this.resources.clear();
        this.resources.addAll(resources);
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(Status status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantUserId() {
        return merchantUserId;
    }

    public String getName() {
        return name;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getHighlightsJson() {
        return highlightsJson;
    }

    public Status getStatus() {
        return status;
    }

    public Set<TourismResource> getResources() {
        return Set.copyOf(resources);
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
