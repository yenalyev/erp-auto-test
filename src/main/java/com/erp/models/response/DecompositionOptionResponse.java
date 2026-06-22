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
public class DecompositionOptionResponse {
    private TechnologicalMapRefResponse technologicalMap;
    private Double outputAmount;
    @Builder.Default
    private List<SimpleEntityResponse> storages = new ArrayList<>();
}
