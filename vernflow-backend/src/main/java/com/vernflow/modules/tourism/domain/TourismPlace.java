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
@Table(name = "tourism_place")
public class TourismPlace {

    public enum VerificationStatus {
        VERIFIED,
        APPROXIMATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 80)
    private String district;

    @Column(length = 255)
    private String address;

    @Column(name = "amap_poi_id", unique = true, length = 64)
    private String amapPoiId;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal latitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private VerificationStatus verificationStatus;

    @Column(name = "source_name", nullable = false, length = 160)
    private String sourceName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TourismPlace() {
    }

    private TourismPlace(
            String city,
            String name,
            String district,
            String address,
            String amapPoiId,
            BigDecimal longitude,
            BigDecimal latitude,
            VerificationStatus verificationStatus,
            String sourceName
    ) {
        this.city = city;
        this.name = name;
        this.district = district;
        this.address = address;
        this.amapPoiId = amapPoiId;
        this.longitude = longitude;
        this.latitude = latitude;
        this.verificationStatus = verificationStatus;
        this.sourceName = sourceName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TourismPlace verified(
            String city,
            String name,
            String district,
            String address,
            String amapPoiId,
            BigDecimal longitude,
            BigDecimal latitude,
            String sourceName
    ) {
        return new TourismPlace(
                city,
                name,
                district,
                address,
                amapPoiId,
                longitude,
                latitude,
                VerificationStatus.VERIFIED,
                sourceName
        );
    }

    public static TourismPlace approximate(
            String city,
            String name,
            String district,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String sourceName
    ) {
        return new TourismPlace(
                city,
                name,
                district,
                address,
                null,
                longitude,
                latitude,
                VerificationStatus.APPROXIMATE,
                sourceName
        );
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

    public String getDistrict() {
        return district;
    }

    public String getAddress() {
        return address;
    }

    public String getAmapPoiId() {
        return amapPoiId;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getSourceName() {
        return sourceName;
    }
}
