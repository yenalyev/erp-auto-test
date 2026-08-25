package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DisassembleItemResponse {
    private Long id;
    private Double amount;
    private SimpleEntityResponse technologicalMap;
    private SimpleEntityResponse itemForDisassemble;
    private SimpleEntityResponse storage;
    private List<ResourceUsageResponse> outputs;
    private LocalDate date;
    private String batchNumber;
    private String notes;
}
