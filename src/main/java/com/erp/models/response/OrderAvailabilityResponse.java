package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderAvailabilityResponse {
    private Long resourceId;
    private String resourceName;
    private BigDecimal requestedQuantity;
    @Builder.Default
    private List<OrderAvailabilityLocationResponse> locations = new ArrayList<>();
}
