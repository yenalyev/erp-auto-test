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
public class DecompositionRequirementsResponse {
    @Builder.Default
    private List<RequirementItemResponse> semiFinished = new ArrayList<>();
    @Builder.Default
    private List<RequirementItemResponse> rawMaterials = new ArrayList<>();
}
