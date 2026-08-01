package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors backend {@code org.pm.tk.dto.response.projectproduction.ProjectProductionResourcesResponse}
 * — response of {@code GET /api/v1/project-production/{productionId}/resources}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionResourcesResponse {
    @Builder.Default
    private List<StageResource> stageResources = new ArrayList<>();

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageResource {
        private SimpleEntityResponse stage;
        private SimpleEntityResponse resource;
        private BigDecimal amountNeeded;
        private BigDecimal amountUsed;
    }
}
