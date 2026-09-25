package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * FAITA resource from {@code GET /integrations/faita/resources}
 * and {@code PUT .../implicit-resources} response.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FaitaResourceResponse {
    private String resourceId;
    private String resourceName;
    /**
     * Multiplier for an implicit resource. Top-level FAITA resources do not expose this field,
     * while legacy implicit records without it are interpreted as one by the API.
     */
    @Builder.Default
    private Integer count = 1;
    @Builder.Default
    private List<ResourceReconciliationResponse> reconciliations = new ArrayList<>();
    @Builder.Default
    private List<FaitaResourceResponse> implicitResources = new ArrayList<>();
}
