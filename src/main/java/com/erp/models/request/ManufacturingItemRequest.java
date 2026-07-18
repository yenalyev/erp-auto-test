package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManufacturingItemRequest {
    private Long productId;
    private Long techMapId;
    private Double amount;
    private LocalDate date;
    private String batchNumber;
    private LocalTime time;

    @Builder.Default
    private List<ProcessResourceOutputRequest> outputs = new ArrayList<>();

    @Builder.Default
    private List<ResourceUsageRequest> inputs = new ArrayList<>();

    @Builder.Default
    private List<AlternativeInputRequest> alternativeInputs = new ArrayList<>();
}
