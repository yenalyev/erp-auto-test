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
public class TechnologicalMapAlternativeGroupRequest {
    private Long id;
    private String name;

    @Builder.Default
    private List<TechnologicalMapAlternativeGroupResourceRequest> alternativeResources = new ArrayList<>();
}
