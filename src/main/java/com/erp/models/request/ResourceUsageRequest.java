package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceUsageRequest {
    private Long resourceId;
    private BigDecimal amount;
    private UUID batchUuid;
    private Long version;
    /** Inventory row explicitly removed by the user; retain its snapshot version for conflict detection. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean removed;
    @Builder.Default
    private List<RelocationItemBatchRequest> batches = new ArrayList<>();

    public ResourceUsageRequest(Long resourceId, Double amount) {
        this.resourceId = resourceId;
        this.amount = amount == null ? null : BigDecimal.valueOf(amount);
    }
}
