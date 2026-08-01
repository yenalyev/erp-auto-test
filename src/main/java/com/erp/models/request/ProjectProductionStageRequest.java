package com.erp.models.request;

import com.erp.enums.ProjectProductionState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ProjectProductionStageRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionStageRequest {
    private String name;
    private String description;
    private String comment;
    private ProjectProductionState state;
    private Integer stageOrder;
    private Integer executionPercentage;
    @Builder.Default
    private List<ProjectProductionStageResourceUsageRequest> projectProductionStageResourceUsages = new ArrayList<>();
}
