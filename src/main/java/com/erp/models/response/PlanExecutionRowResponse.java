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
public class PlanExecutionRowResponse {
    private SimpleEntityResponse resource;
    private SimpleEntityResponse resourceCategory;
    private SimpleEntityResponse unit;
    private List<Double> producedByDay;
    private Double totalProduced;
    private Double planGoal;
    private Double planExecutionPercentage;
    private Double storageRemainder;
}
