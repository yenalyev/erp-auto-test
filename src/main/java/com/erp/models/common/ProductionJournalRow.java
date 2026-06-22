package com.erp.models.common;

import com.erp.models.response.ManufacturingItemResponse;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Normalized production journal row for cross-checking UI table cells against API data.
 */
@Value
@Builder
public class ProductionJournalRow {

    String productName;
    LocalDate date;
    LocalTime time;
    double amount;
    String technologicalMapName;
    String batchNumber;

    public static ProductionJournalRow fromApi(ManufacturingItemResponse item) {
        Objects.requireNonNull(item, "production item");
        return ProductionJournalRow.builder()
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .date(item.getDate())
                .time(item.getTime())
                .amount(item.getAmount() != null ? item.getAmount() : 0.0)
                .technologicalMapName(item.getTechnologicalMap() != null
                        ? item.getTechnologicalMap().getName() : null)
                .batchNumber(item.getBatchNumber())
                .build();
    }
}
