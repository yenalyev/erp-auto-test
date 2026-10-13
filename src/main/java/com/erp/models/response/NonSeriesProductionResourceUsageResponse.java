package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NonSeriesProductionResourceUsageResponse {
    private Long id;
    private SimpleEntityResponse resource;
    private BigDecimal amount;
    private UUID batchUuid;
    private MeasurementUnitResponse unit;
}
