package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyPlanExecutionResponse {
    private LocalDate date;
    private List<ResourcePlanExecutionResponse> items;
    private List<ResourceSufficiencyResponse> materials;
    private List<AlternativeResourceSufficiencyResponse> alternatives = new ArrayList<>();

}
