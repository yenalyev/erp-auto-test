package com.erp.utils.helpers;

import com.erp.fixtures.ProductionFixture;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class ProductionTagAssertions {

    public static void assertFilteredByTag(ProductionFixture fixture,
                                           long storageId,
                                           String tag,
                                           Long... expectedProductionIds) {
        Objects.requireNonNull(tag, "tag");
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .tags(List.of(tag))
                .pageSize(500)
                .build();

        List<ManufacturingItemResponse> filtered = fixture.getJournalPage(query);
        Allure.parameter("filterTag", tag);

        for (Long expectedId : expectedProductionIds) {
            assertThat(filtered)
                    .as("Журнал з tags=%s має містити виробництво id=%s", tag, expectedId)
                    .anyMatch(item -> Objects.equals(item.getId(), expectedId));
        }
    }

    public static void assertNotFilteredByTag(ProductionFixture fixture,
                                              long storageId,
                                              String tag,
                                              Long excludedProductionId) {
        Objects.requireNonNull(tag, "tag");
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .tags(List.of(tag))
                .pageSize(500)
                .build();

        List<ManufacturingItemResponse> filtered = fixture.getJournalPage(query);
        Allure.parameter("filterTag", tag);
        Allure.parameter("excludedProductionId", excludedProductionId);

        assertThat(filtered)
                .as("Журнал з tags=%s не повинен містити виробництво id=%s", tag, excludedProductionId)
                .noneMatch(item -> Objects.equals(item.getId(), excludedProductionId));
    }

    public static void assertTagStatisticsContains(ProductionFixture fixture,
                                                   long storageId,
                                                   String tag,
                                                   long minCount) {
        List<ProductionProcessTagStatisticResponse> stats =
                fixture.getTagStatistics(ProductionJournalQuery.uiDefaults(storageId));
        Allure.parameter("statisticsTag", tag);
        Allure.parameter("minCount", minCount);

        assertThat(stats)
                .as("tag-statistics має містити тег %s з count >= %s", tag, minCount)
                .anyMatch(stat -> tag.equals(stat.getTag()) && stat.getCount() != null && stat.getCount() >= minCount);
    }

    public static void assertProductionProcessTagsCatalogContains(ProductionFixture fixture,
                                                                  long storageId,
                                                                  String tag) {
        Collection<String> catalog = fixture.getProductionProcessTags(storageId);
        Allure.parameter("catalogTag", tag);
        assertThat(catalog)
                .as("Каталог production-process-tags для storageId=%s", storageId)
                .contains(tag);
    }
}
