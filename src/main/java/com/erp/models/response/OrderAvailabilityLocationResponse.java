package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderAvailabilityLocationResponse {
    private Long storageId;
    private String storageName;
    private BigDecimal amount;
    private BigDecimal heldAmount;
    /** Slice of {@code heldAmount} reserved by this order (CPMA-725). */
    private BigDecimal heldByThisOrder;
}
