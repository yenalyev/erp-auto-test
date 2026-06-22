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
public class DecompositionBlockItemResponse {
    private ResourceResponse resource;
    private Double requiredAmount;
    private Double assignedAmount;
    private boolean complete;
    private boolean autoAssignable;
    @Builder.Default
    private List<DecompositionAssignmentResponse> assignments = new ArrayList<>();
    @Builder.Default
    private List<DecompositionOptionResponse> options = new ArrayList<>();
    @Builder.Default
    private List<DecompositionAssignmentResponse> suggestedAssignments = new ArrayList<>();
}
