package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssemblyComponentResponse {
    private SimpleEntityResponse resource;
    private String unit;
    private BigDecimal requiredPerUnit;
    private BigDecimal availableStock;

    /** Active PRODUCTION maps that output this component (all storages; UI filters by role). */
    @Builder.Default
    private List<AssemblyComponentTechMapResponse> technologicalMaps = new ArrayList<>();
}
