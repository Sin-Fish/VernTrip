package com.vernflow.modules.tourism.dto;

import java.math.BigDecimal;
import java.util.List;

public record TravelPlanResponse(
        Long id,
        Long parentPlanId,
        int version,
        String title,
        String createdAt,
        String status,
        String engine,
        RequestSnapshot request,
        int match,
        String summary,
        List<String> reasons,
        List<DayPlan> days,
        BudgetBreakdown budget,
        List<String> risks,
        List<TourismResourceResponse> sources
) {

    public record RequestSnapshot(
            String destination,
            String departure,
            String date,
            int days,
            String travelers,
            BigDecimal budget,
            String pace,
            List<String> interests,
            String notes
    ) {
    }

    public record DayPlan(
            int day,
            String dateLabel,
            String title,
            List<PlanItem> items
    ) {
    }

    public record PlanItem(
            Long resourceId,
            String time,
            String label,
            String title,
            String description,
            String tag,
            String tone,
            BigDecimal cost,
            String sourceName,
            String address,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record BudgetBreakdown(
            BigDecimal total,
            BigDecimal stay,
            BigDecimal food,
            BigDecimal activity,
            BigDecimal transportation,
            BigDecimal reserve
    ) {
    }
}
