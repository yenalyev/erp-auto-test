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
    @Builder.Default
    private List<ResourceReconciliationResponse> reconciliations = new ArrayList<>();
    @Builder.Default
    private List<FaitaResourceResponse> implicitResources = new ArrayList<>();
}
