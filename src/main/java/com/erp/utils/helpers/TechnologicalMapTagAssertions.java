package com.erp.utils.helpers;

import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.query.TechnologicalMapListQuery;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import com.erp.models.response.TechnologicalMapResponse;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class TechnologicalMapTagAssertions {

    public static void assertFilteredByTag(TechnologicalMapFixture fixture,
                                           long storageId,
                                           String tag,
                                           Long... expectedTechMapIds) {
        Objects.requireNonNull(tag, "tag");
        TechnologicalMapListQuery query = TechnologicalMapListQuery.forStorage(storageId)
                .toBuilder()
                .tags(List.of(tag))
                .pageSize(500)
                .build();

        List<TechnologicalMapResponse> filtered = fixture.listByQuery(query);
        Allure.parameter("filterTag", tag);

        for (Long expectedId : expectedTechMapIds) {
            assertThat(filtered)
                    .as("Список техкарт з tags=%s має містити id=%s", tag, expectedId)
                    .anyMatch(item -> Objects.equals(item.getId(), expectedId));
        }
    }

    public static void assertNotFilteredByTag(TechnologicalMapFixture fixture,
                                              long storageId,
                                              String tag,
                                              Long excludedTechMapId) {
        Objects.requireNonNull(tag, "tag");
        TechnologicalMapListQuery query = TechnologicalMapListQuery.forStorage(storageId)
                .toBuilder()
                .tags(List.of(tag))
                .pageSize(500)
                .build();

        List<TechnologicalMapResponse> filtered = fixture.listByQuery(query);
        Allure.parameter("filterTag", tag);
        Allure.parameter("excludedTechMapId", excludedTechMapId);

        assertThat(filtered)
                .as("Список техкарт з tags=%s не повинен містити id=%s", tag, excludedTechMapId)
                .noneMatch(item -> Objects.equals(item.getId(), excludedTechMapId));
    }

    public static void assertTagStatisticsContains(TechnologicalMapFixture fixture,
                                                   long storageId,
                                                   String tag,
                                                   long minCount) {
        List<ProductionProcessTagStatisticResponse> stats =
                fixture.getTagStatistics(TechnologicalMapListQuery.forTagStatistics(storageId));
        Allure.parameter("statisticsTag", tag);
        Allure.parameter("minCount", minCount);

        assertThat(stats)
                .as("tech-map tag-statistics має містити тег %s з count >= %s", tag, minCount)
                .anyMatch(stat -> tag.equals(stat.getTag()) && stat.getCount() != null && stat.getCount() >= minCount);
    }

    public static void assertTechnologicalMapTagsCatalogContains(TechnologicalMapFixture fixture,
                                                                 long storageId,
                                                                 String tag) {
        Collection<String> catalog = fixture.getTechnologicalMapTags(storageId);
        Allure.parameter("catalogTag", tag);
        assertThat(catalog)
                .as("Каталог technological-map-tags для storageId=%s", storageId)
                .contains(tag);
    }
}
