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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceRelocationSumViewerResponse {
    private Long resourceId;
    private String resourceName;
    private String shortName;
    private BigDecimal amount;
    @Builder.Default
    private List<String> groupedFrom = new ArrayList<>();
}
