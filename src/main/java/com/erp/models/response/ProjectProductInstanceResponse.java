package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors backend {@code org.pm.tk.dto.response.projectproduction.ProjectProductInstanceResponse}.
 * Returned by {@code GET /api/v1/project-production/products} — one entry per finished
 * project production batch (serial number) for the given {@code category} (project product name).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductInstanceResponse {
    private Long resourceId;
    private String resourceName;
    private String serialNumber;
}
