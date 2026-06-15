package com.erp.models.request;

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
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NonSeriesProductionRequest {
    private LocalDate start;
    private LocalDate end;
    private Integer workerQty;
    private String product;
    private BigDecimal amount;
    private String description;
    private NonSeriesProductionStatus status;
    private Long storageId;
    @Builder.Default
    private List<NonSeriesProductionResourceUsageRequest> resourceUsageList = new ArrayList<>();
}
