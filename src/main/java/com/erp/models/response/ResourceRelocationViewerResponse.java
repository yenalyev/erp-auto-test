package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceRelocationViewerResponse {
    private Long relocationId;
    private SimpleEntityResponse sender;
    private SimpleEntityResponse recipient;
    private LocalDate date;
    private String state;
    private BigDecimal amount;
    private SimpleEntityResponse product;
    private String unit;
    private Boolean isProduct;
    @Builder.Default
    private List<ResourceIngredientResponse> ingredients = new ArrayList<>();
    private String invoiceNumber;
    private Boolean hasInvoice;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceIngredientResponse {
        private Long resourceId;
        private String name;
        private BigDecimal usage;
        private BigDecimal totallyUsage;
        private String unit;
    }
}
