package com.erp.models.request;

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
public class PlanRequest {
    private String description;
    private Long storageId;
    @Builder.Default
    private List<ResourceUsageRequest> output = new ArrayList<>();
    private Integer month;
    private Integer year;
}
