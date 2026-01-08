package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanStatisticsResponse {
    private PlanSummaryResponse summary;
    private List<PlanResourceSummaryResponse> resources;
    private List<DailyPlanExecutionResponse> dailyProduction;
}
