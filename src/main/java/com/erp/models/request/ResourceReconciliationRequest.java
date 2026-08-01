package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Body for {@code POST /api/v1/resources/reconciliations} (FLIGHT ↔ ERP mapping).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceReconciliationRequest {
    /** {@code FLIGHT} for FAITA / Fight sync. */
    private String source;
    private String externalId;
    private String externalName;
    @Builder.Default
    private List<Long> resourceIds = new ArrayList<>();
}
