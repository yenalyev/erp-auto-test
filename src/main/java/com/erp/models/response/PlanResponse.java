package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanResponse {
    private Long id;
    private String description;
    private SimpleEntityResponse storage;
    @Builder.Default
    private List<ResourceUsageResponse> output = new ArrayList<>();
    private Integer month;
    private Integer year;
    private LocalDate from;
    private LocalDate to;
}
