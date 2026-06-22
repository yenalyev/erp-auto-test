package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecompositionResponse {
    @Builder.Default
    private List<DecompositionBlockResponse> blocks = new ArrayList<>();
    private DecompositionBlockResponse nextBlock;
    private boolean complete;
    private DecompositionRequirementsResponse requirements;
    @Builder.Default
    private List<LocationPlanResponse> locationPlans = new ArrayList<>();
}
