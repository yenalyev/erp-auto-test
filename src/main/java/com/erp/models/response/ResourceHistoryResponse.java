package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceHistoryResponse {
    private String date;
    private Double amount;
    private ResourceResponse resource;
    private String resourceOperationType;
    private SimpleEntityResponse fromUnit;
    private SimpleEntityResponse toUnit;
    private String comment;
}
