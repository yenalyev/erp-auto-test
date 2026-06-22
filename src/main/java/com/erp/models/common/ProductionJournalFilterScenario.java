package com.erp.models.common;

import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ProductionJournalFilterScenario(
        String name,
        ManufacturingItemResponse anchor,
        ProductionJournalQuery query,
        String productTerm,
        LocalDate startDate,
        LocalDate endDate,
        Long categoryId,
        String categoryName
) {
}
