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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_request")
public class TripRequest {

    public enum Status {
        ACTIVE,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String destination;

    @Column(name = "departure_city", length = 80)
    private String departureCity;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(nullable = false)
    private int days;

    @Column(name = "traveler_type", nullable = false, length = 40)
    private String travelerType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(nullable = false, length = 40)
    private String pace;

    @Column(name = "interests_json", nullable = false, columnDefinition = "TEXT")
    private String interestsJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TripRequest() {
    }

    private TripRequest(
            Long userId,
            String destination,
            String departureCity,
            LocalDate travelDate,
            int days,
            String travelerType,
            BigDecimal budget,
            String pace,
            String interestsJson,
            String notes
    ) {
        this.userId = userId;
        this.destination = destination;
        this.departureCity = departureCity;
        this.travelDate = travelDate;
        this.days = days;
        this.travelerType = travelerType;
        this.budget = budget;
        this.pace = pace;
        this.interestsJson = interestsJson;
        this.notes = notes;
        this.status = Status.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static TripRequest create(
            Long userId,
            String destination,
            String departureCity,
            LocalDate travelDate,
            int days,
            String travelerType,
            BigDecimal budget,
            String pace,
            String interestsJson,
            String notes
    ) {
        return new TripRequest(
                userId,
                destination,
                departureCity,
                travelDate,
                days,
                travelerType,
                budget,
                pace,
                interestsJson,
                notes
        );
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDestination() {
        return destination;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public int getDays() {
        return days;
    }

    public String getTravelerType() {
        return travelerType;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public String getPace() {
        return pace;
    }

    public String getInterestsJson() {
        return interestsJson;
    }

    public String getNotes() {
        return notes;
    }
}
