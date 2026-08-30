package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TripRequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {

    List<TripRequest> findAllByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime createdAt);
}
