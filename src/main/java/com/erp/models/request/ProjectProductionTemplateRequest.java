package com.erp.models.request;

import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ProjectProductionTemplateRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionTemplateRequest {
    private String name;
    private ProjectProductionState state;
    private ProjectProductionType type;
    private String description;
    private Long storageId;
    private Long projectCategoryId;
    private Long projectProductId;
    @Builder.Default
    private List<ProjectProductionStageRequest> projectProductionStages = new ArrayList<>();
}
