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
public class UnitShortStatsResponse {
    private Long unitId;
    private String unitName;
    private Integer flyPointsCount;
    private Integer crewsCount;
    private BigDecimal ammunitionIncome;
    private BigDecimal ammunitionOutcome;
    private BigDecimal writeOffCompleted;
    private BigDecimal writeOffFailed;
    private BigDecimal writeOffSkipped;
    private BigDecimal writeOffPending;
}
