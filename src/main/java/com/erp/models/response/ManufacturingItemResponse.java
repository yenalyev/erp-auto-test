package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManufacturingItemResponse {
    private Long id;
    private Double amount;
    private SimpleEntityResponse technologicalMap;
    private SimpleEntityResponse product;
    private SimpleEntityResponse storage;
    private List<ResourceUsageResponse> input;
    private LocalDate date;
    private String batchNumber;
    private LocalTime time;
    private String notes;
}
