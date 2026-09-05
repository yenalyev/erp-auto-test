package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrewResourceCategoryStockResponse {
    private Long flyPointId;
    private String flyPointName;
    private Long crewId;
    private String crewName;
    private Long categoryId;
    private String categoryName;
    private List<CrewResourceStockItemResponse> resourceStocks;
}
