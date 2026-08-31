package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryRequest {
    @Builder.Default
    private List<ResourceUsageRequest> resources = new ArrayList<>();

    /** Optional free-text note copied onto inventory history rows (ADDED_INV / REMOVED_INV). */
    private String comment;
}
