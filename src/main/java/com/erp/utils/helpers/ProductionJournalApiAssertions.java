package com.erp.utils.helpers;

import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class ProductionJournalApiAssertions {

    public static void assertAnchorPresent(List<ManufacturingItemResponse> filtered,
                                           ManufacturingItemResponse anchor,
                                           String scenario) {
        assertThat(filtered)
                .as("Фільтр «%s» має містити опорний запис id=%s", scenario, anchor.getId())
                .anyMatch(item -> Objects.equals(item.getId(), anchor.getId()));
    }

    public static void assertAllMatchQuery(List<ManufacturingItemResponse> filtered,
                                           ProductionJournalFilterScenario scenario,
                                           Map<Long, Long> productCategoryMap) {
        ProductionJournalQuery query = scenario.query();
        String product = query.getProduct();
        LocalDate startDate = query.getStartDate();
        LocalDate endDate = query.getEndDate();
        Long categoryId = query.getCategoryId();

        for (ManufacturingItemResponse item : filtered) {
            if (product != null && !product.isBlank()) {
                assertThat(item.getProduct().getName().toLowerCase(Locale.ROOT))
                        .as("product filter for id=%s", item.getId())
                        .contains(product.toLowerCase(Locale.ROOT));
            }
            if (startDate != null) {
                assertThat(item.getDate())
                        .as("startDate filter for id=%s", item.getId())
                        .isAfterOrEqualTo(startDate);
            }
            if (endDate != null) {
                assertThat(item.getDate())
                        .as("endDate filter for id=%s", item.getId())
                        .isBeforeOrEqualTo(endDate);
            }
            if (categoryId != null) {
                Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
                assertThat(productCategoryMap.get(productId))
                        .as("category filter for product id=%s", productId)
                        .isEqualTo(categoryId);
            }
        }
    }

    public static void assertFilteredSubsetOfBaseline(List<ManufacturingItemResponse> filtered,
                                                      List<ManufacturingItemResponse> baseline,
                                                      String scenario) {
        List<Long> baselineIds = baseline.stream().map(ManufacturingItemResponse::getId).toList();
        assertThat(filtered)
                .as("Результат фільтра «%s» має бути підмножиною базового журналу", scenario)
                .allMatch(item -> baselineIds.contains(item.getId()));
    }
}
