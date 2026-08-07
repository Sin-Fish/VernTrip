package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TripFeedback;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripFeedbackRepository extends JpaRepository<TripFeedback, Long> {

    boolean existsByUserIdAndPlanIdAndType(Long userId, Long planId, TripFeedback.Type type);

    void deleteByUserIdAndPlanIdAndType(Long userId, Long planId, TripFeedback.Type type);
}
