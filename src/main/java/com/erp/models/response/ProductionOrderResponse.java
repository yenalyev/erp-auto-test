package com.erp.models.response;

import com.erp.enums.ProductionOrderState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductionOrderResponse {
    private Long id;
    private ProductionOrderState state;
    private String description;
    private SimpleEntityResponse targetStorage;
    private LocalDate targetDate;
    private List<ResourceUsageResponse> output;
    private Integer totalTasks;
    private Integer completedTasks;
    private String createdBy;
    private Instant createdAt;
}
