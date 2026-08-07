package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TripRequest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {
}
