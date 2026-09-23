package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TotalPlanExecutionResponse {
    private Double todayTotalProduced;
    private Double totalGoal;
    private Double totalProducedGoal;
    private Double totalProduced;
    private Double totalPercentage;
    private Integer dayPassed;
    private Double timePercentage;
}
