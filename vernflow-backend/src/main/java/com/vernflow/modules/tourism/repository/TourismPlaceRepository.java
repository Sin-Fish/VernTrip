package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TourismPlace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourismPlaceRepository extends JpaRepository<TourismPlace, Long> {

    Optional<TourismPlace> findByAmapPoiId(String amapPoiId);
}
