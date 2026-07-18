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
public class TechnologicalMapAlternativeGroupResponse {
    private Long id;
    private String name;

    @Builder.Default
    private List<TechnologicalMapAlternativeGroupResourceResponse> alternativeResources = new ArrayList<>();
}
