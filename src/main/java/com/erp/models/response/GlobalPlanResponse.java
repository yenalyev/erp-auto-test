package com.erp.models.response;

import com.erp.models.request.DecompositionRequest;
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
public class GlobalPlanResponse {
    private Long id;
    private String description;
    private Integer month;
    private Integer year;
    private LocalDate from;
    private LocalDate to;
    @Builder.Default
    private List<ResourceUsageResponse> output = new ArrayList<>();
    @Builder.Default
    private List<GeneratedPlanResponse> generatedPlans = new ArrayList<>();
    private DecompositionRequest decomposition;
}
