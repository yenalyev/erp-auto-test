package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ResourceToRollbackRequest}.
 * Optional body for {@code DELETE /api/v1/project-production/{id}} — resources to return to stock.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceToRollbackRequest {
    private Long stageId;
    private Long resourceId;
    private BigDecimal amount;
}
