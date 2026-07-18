package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Item of {@code PlanSufficiencyResponse.dailyNeed} (GET /statistics/plan).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanDailyNeedResponse {
    private ResourceResponse resource;
    private Double dailyNeed;
    private Double storage;
}
