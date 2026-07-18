package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Maps backend {@code PlanSufficiencyResponse} (GET /api/v1/statistics/plan).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanStatisticsResponse {
    private List<ResourceUsageResponse> dailyPlan;
    private List<PlanDailyNeedResponse> dailyNeed;
}
