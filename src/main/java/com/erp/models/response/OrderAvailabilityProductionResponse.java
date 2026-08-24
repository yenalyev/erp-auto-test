package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderAvailabilityProductionResponse {
    private Long productionOrderId;
    private String state;
    private SimpleEntityResponse targetStorage;
    private LocalDate targetDate;
    private BigDecimal plannedAmount;
    private BigDecimal producedAmount;
    private BigDecimal claimedByOthers;
    private BigDecimal claimedByThisOrder;
    private BigDecimal remainingForThisOrder;
}
