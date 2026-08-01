package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ProjectProductionStageResourceUsageRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductionStageResourceUsageRequest {
    private Long id;
    private Long resourceId;
    private BigDecimal amountNeeded;
    private BigDecimal amountUsed;
}
