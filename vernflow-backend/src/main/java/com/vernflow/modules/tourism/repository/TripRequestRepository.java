package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TripRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import com.vernflow.modules.tourism.domain.TravelPlan;

import java.time.LocalDateTime;
import java.util.List;

public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {

    List<TripRequest> findAllByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime createdAt);

    @Query("select r from TripRequest r where r.userId = :userId and exists "
            + "(select p.id from TravelPlan p where p.tripRequestId = r.id and p.userId = :userId "
            + "and p.status in :statuses) order by r.createdAt desc")
    List<TripRequest> findSavedHistory(@Param("userId") Long userId,
            @Param("statuses") List<TravelPlan.Status> statuses, Pageable pageable);
}
