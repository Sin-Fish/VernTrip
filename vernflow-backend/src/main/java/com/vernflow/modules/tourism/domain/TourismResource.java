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
@Table(name = "tourism_resource")
public class TourismResource {

    public enum Category {
        SCENIC,
        CULTURE,
        EXPERIENCE,
        FOOD,
        LODGING,
        LEISURE
    }

    public enum PublicationStatus {
        DRAFT,
        PUBLISHED,
        INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Column(length = 80)
    private String district;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "suitable_people_json", nullable = false, columnDefinition = "TEXT")
    private String suitablePeopleJson;

    @Column(length = 255)
    private String address;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "source_name", nullable = false, length = 160)
    private String sourceName;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "open_hours", length = 160)
    private String openHours;

    @Column(name = "price_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PublicationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TourismResource() {
    }

    private TourismResource(
            String city,
            String name,
            Category category,
            String district,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String address,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax,
            PublicationStatus status
    ) {
        this.city = city;
        this.name = name;
        this.category = category;
        this.district = district;
        this.summary = summary;
        this.tagsJson = tagsJson;
        this.suitablePeopleJson = suitablePeopleJson;
        this.address = address;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.openHours = openHours;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TourismResource published(
            String city,
            String name,
            Category category,
            String district,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String address,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax
    ) {
        return new TourismResource(
                city,
                name,
                category,
                district,
                summary,
                tagsJson,
                suitablePeopleJson,
                address,
                sourceName,
                sourceUrl,
                openHours,
                priceMin,
                priceMax,
                PublicationStatus.PUBLISHED
        );
    }

    public static TourismResource draft(
            String city,
            String name,
            Category category,
            String district,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String address,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax
    ) {
        return new TourismResource(
                city,
                name,
                category,
                district,
                summary,
                tagsJson,
                suitablePeopleJson,
                address,
                sourceName,
                sourceUrl,
                openHours,
                priceMin,
                priceMax,
                PublicationStatus.DRAFT
        );
    }

    public void changeStatus(PublicationStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCoordinates(BigDecimal longitude, BigDecimal latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCity() {
        return city;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public String getDistrict() {
        return district;
    }

    public String getSummary() {
        return summary;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public String getSuitablePeopleJson() {
        return suitablePeopleJson;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getOpenHours() {
        return openHours;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public BigDecimal getPriceMax() {
        return priceMax;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
