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
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private TourismPlace place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "suitable_people_json", nullable = false, columnDefinition = "TEXT")
    private String suitablePeopleJson;

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

    @Column(name = "reservation_required", nullable = false)
    private boolean reservationRequired;

    @Column(name = "daily_capacity")
    private Integer dailyCapacity;

    @Column(name = "available_capacity")
    private Integer availableCapacity;

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
            TourismPlace place,
            String name,
            Category category,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax,
            PublicationStatus status
    ) {
        this.city = place.getCity();
        this.name = name;
        this.place = place;
        this.category = category;
        this.summary = summary;
        this.tagsJson = tagsJson;
        this.suitablePeopleJson = suitablePeopleJson;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.openHours = openHours;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.reservationRequired = false;
        this.dailyCapacity = null;
        this.availableCapacity = null;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TourismResource published(
            TourismPlace place,
            String name,
            Category category,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax
    ) {
        return new TourismResource(
                place,
                name,
                category,
                summary,
                tagsJson,
                suitablePeopleJson,
                sourceName,
                sourceUrl,
                openHours,
                priceMin,
                priceMax,
                PublicationStatus.PUBLISHED
        );
    }

    public static TourismResource draft(
            TourismPlace place,
            String name,
            Category category,
            String summary,
            String tagsJson,
            String suitablePeopleJson,
            String sourceName,
            String sourceUrl,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax
    ) {
        return new TourismResource(
                place,
                name,
                category,
                summary,
                tagsJson,
                suitablePeopleJson,
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

    public void updateDetails(
            String name,
            Category category,
            String summary,
            String tagsJson,
            String openHours,
            BigDecimal priceMin,
            BigDecimal priceMax,
            boolean reservationRequired,
            Integer dailyCapacity,
            Integer availableCapacity
    ) {
        this.name = name;
        this.category = category;
        this.summary = summary;
        this.tagsJson = tagsJson;
        this.openHours = openHours;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.reservationRequired = reservationRequired;
        this.dailyCapacity = reservationRequired ? dailyCapacity : null;
        this.availableCapacity = reservationRequired ? availableCapacity : null;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAvailableForPlanning() {
        return status == PublicationStatus.PUBLISHED
                && (!reservationRequired || availableCapacity == null || availableCapacity > 0);
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

    public TourismPlace getPlace() {
        return place;
    }

    public String getDistrict() {
        return place.getDistrict();
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
        return place.getAddress();
    }

    public String getAmapPoiId() {
        return place.getAmapPoiId();
    }

    public BigDecimal getLongitude() {
        return place.getLongitude();
    }

    public BigDecimal getLatitude() {
        return place.getLatitude();
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

    public boolean isReservationRequired() {
        return reservationRequired;
    }

    public Integer getDailyCapacity() {
        return dailyCapacity;
    }

    public Integer getAvailableCapacity() {
        return availableCapacity;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
