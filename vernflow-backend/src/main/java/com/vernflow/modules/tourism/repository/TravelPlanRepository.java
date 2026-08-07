package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TravelPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    Optional<TravelPlan> findByIdAndUserId(Long id, Long userId);

    List<TravelPlan> findAllByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, TravelPlan.Status status);
}
