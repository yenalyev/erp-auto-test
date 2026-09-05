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
public class TechnologicalMapResourceUsageResponse {
    private Long id;
    private String name;
    private Double amount;
    private String unit;
    @Builder.Default
    private List<SimpleEntityResponse> storages = new ArrayList<>();
    @Builder.Default
    private List<TechnologicalMapComponentResponse> components = new ArrayList<>();
}
