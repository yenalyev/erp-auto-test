package com.erp.models.response;

import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionResponse {
    private Long id;
    private String createdBy;
    private LocalDate start;
    private Instant createdAt;
    private LocalDate deadlineTo;
    private ProjectProductionState state;
    private ProjectProductionType type;
    private String serialNumber;
    private String description;
    private SimpleEntityResponse storage;
    private SimpleEntityResponse projectCategory;
    private SimpleEntityResponse projectProduct;
    @Builder.Default
    private List<ProjectProductionStageResponse> projectProductionStages = new ArrayList<>();
    @Builder.Default
    private List<ProjectProductPropertyResponse> specificProperties = new ArrayList<>();
    @Builder.Default
    private List<SimpleEntityResponse> equipments = new ArrayList<>();
}
