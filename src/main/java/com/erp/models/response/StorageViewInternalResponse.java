package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flat storage hierarchy node for {@code GET /api/v1/internal/storages/structure}.
 * Mirrors backend {@code StorageViewInternalResponse}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageViewInternalResponse {
    private Long id;
    private String name;
    /** Null for root storages. */
    private Long parentId;
}
