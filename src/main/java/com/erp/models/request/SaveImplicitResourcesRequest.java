package com.erp.models.request;

import com.erp.models.response.FaitaResourceResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Body for {@code PUT /api/v1/integrations/faita/resources/{externalId}/implicit-resources}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveImplicitResourcesRequest {
    private String externalId;
    private String externalName;
    @Builder.Default
    private List<FaitaResourceResponse> implicitResources = new ArrayList<>();
}
