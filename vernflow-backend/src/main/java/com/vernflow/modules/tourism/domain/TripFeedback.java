package com.vernflow.modules.tourism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_feedback")
public class TripFeedback {

    public enum Type {
        COLLECTED,
        ADOPTED,
        DISLIKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 24)
    private Type type;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TripFeedback() {
    }

    private TripFeedback(Long userId, Long planId, Type type, String note) {
        this.userId = userId;
        this.planId = planId;
        this.type = type;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public static TripFeedback create(Long userId, Long planId, Type type, String note) {
        return new TripFeedback(userId, planId, type, note);
    }
}
