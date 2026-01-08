package com.erp.models.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanSummaryResponse {
    private Double goal;
    private Double current;
    private Double currentPercentage;
    private Double differencePercentage;
}
