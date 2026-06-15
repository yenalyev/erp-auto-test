package com.erp.models.response;

import com.erp.enums.NonSeriesProductionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NonSeriesProductionResponse {
    private Long id;
    private LocalDate start;
    private LocalDate end;
    private Integer workerQty;
    private String product;
    private BigDecimal amount;
    private String description;
    private NonSeriesProductionStatus status;
    private SimpleEntityResponse storage;
    @Builder.Default
    private List<NonSeriesProductionResourceUsageResponse> resourceUsageList = new ArrayList<>();
}
