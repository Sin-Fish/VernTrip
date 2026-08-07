package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TravelPlanItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanItemRepository extends JpaRepository<TravelPlanItem, Long> {

    List<TravelPlanItem> findAllByPlanIdOrderByDayNumberAscSequenceNoAsc(Long planId);
}
