package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequirementsExportRequest {
    private String periodLabel;
    @Builder.Default
    private List<RequirementsExportRow> semiFinished = new ArrayList<>();
    @Builder.Default
    private List<RequirementsExportRow> rawMaterials = new ArrayList<>();

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequirementsExportRow {
        private String name;
        private Double requiredAmount;
        private String unitShortName;
        private Double totalStock;
    }
}
