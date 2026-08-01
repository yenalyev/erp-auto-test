package com.erp.models.request;

import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ProjectProductionRequest}.
 * Sent as multipart part {@code request} on {@code POST /api/v1/project-production}
 * and as plain JSON body on {@code PUT /api/v1/project-production/{id}}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionRequest {
    private String name;
    private LocalDate start;
    private LocalDate deadlineTo;
    private ProjectProductionState state;
    private ProjectProductionType type;
    private String serialNumber;
    private String description;
    private Long storageId;
    private Long projectCategoryId;
    private Long projectProductId;
    @Builder.Default
    private List<Long> equipments = new ArrayList<>();
    @Builder.Default
    private List<ProjectProductionStageRequest> projectProductionStages = new ArrayList<>();
    @Builder.Default
    private List<ProjectProductPropertyRequest> specificProperties = new ArrayList<>();
}
