package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TravelPlanItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TravelPlanItemRepository extends JpaRepository<TravelPlanItem, Long> {

    List<TravelPlanItem> findAllByPlanIdOrderByDayNumberAscSequenceNoAsc(Long planId);

    @Query("""
            select item.resourceId, count(item.id)
            from TravelPlanItem item, TravelPlan plan
            where item.planId = plan.id
              and item.resourceId is not null
              and plan.createdAt >= :since
            group by item.resourceId
            order by count(item.id) desc
            """)
    List<Object[]> countResourceCitationsSince(@Param("since") LocalDateTime since);
}
