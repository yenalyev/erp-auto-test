package com.erp.models.response;

import com.erp.enums.RelocationState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchInspectionResponse {
    private UUID id;
    private String name;
    private SimpleEntityResponse resource;
    private Boolean legacy;
    private Instant createdAt;
    @Builder.Default
    private List<Production> productions = new ArrayList<>();
    @Builder.Default
    private List<Ingredient> ingredients = new ArrayList<>();
    @Builder.Default
    private List<Movement> movements = new ArrayList<>();
    @Builder.Default
    private List<Stock> stock = new ArrayList<>();

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Production {
        private Long id;
        private LocalDate date;
        private SimpleEntityResponse storage;
        private SimpleEntityResponse techMap;
        private SimpleEntityResponse product;
        private BigDecimal amount;
        private String unit;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ingredient {
        private Long productionId;
        private SimpleEntityResponse techMap;
        private SimpleEntityResponse resource;
        private String batchName;
        private BigDecimal amount;
        private String unit;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Movement {
        private Long relocationId;
        private Instant dateTime;
        private SimpleEntityResponse sender;
        private SimpleEntityResponse recipient;
        private RelocationState state;
        private String invoiceNumber;
        private Boolean canGenerateInvoice;
        private Boolean hasExternalInvoicePhoto;
        private SimpleEntityResponse resource;
        private BigDecimal amount;
        private String unit;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stock {
        private SimpleEntityResponse storage;
        private SimpleEntityResponse resource;
        private BigDecimal amount;
        private String unit;
    }
}
