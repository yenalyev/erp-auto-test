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
public class OrderRelocationTaskLineResponse {
    private Long orderLineId;
    private SimpleEntityResponse resource;
    private String unit;
    private BigDecimal amount;
    private BigDecimal availableAmount;
}
