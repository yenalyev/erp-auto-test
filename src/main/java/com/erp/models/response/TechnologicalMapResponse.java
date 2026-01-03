package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechnologicalMapResponse {
    private Long id;
    private String name;
    private List<ResourceUsageResponse> input;
    private List<ResourceUsageResponse> output;
    private List<AlternativeGroupResponse> alternatives;
}
