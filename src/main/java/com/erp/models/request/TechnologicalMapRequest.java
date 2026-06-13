package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechnologicalMapRequest {
    private String name;
    private String type;
    private List<ResourceUsageRequest> input;
    private List<ResourceUsageRequest> output;
    private Set<Long> storageIds;
}
