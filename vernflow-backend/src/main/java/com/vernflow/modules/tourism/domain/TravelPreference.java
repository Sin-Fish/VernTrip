package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_preference")
public class TravelPreference {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "interests_json", nullable = false, columnDefinition = "TEXT")
    private String interestsJson;
    @Column(name = "visited_cities_json", nullable = false, columnDefinition = "TEXT")
    private String visitedCitiesJson;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TravelPreference() { }

    public TravelPreference(Long userId, String interestsJson, String visitedCitiesJson) {
        this.userId = userId;
        this.interestsJson = interestsJson;
        this.visitedCitiesJson = visitedCitiesJson;
        this.updatedAt = LocalDateTime.now();
    }

    public String getInterestsJson() { return interestsJson; }
    public String getVisitedCitiesJson() { return visitedCitiesJson; }
}
