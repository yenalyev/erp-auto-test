package com.erp.utils.helpers;

import com.erp.fixtures.ProductionFixture;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.pages.ProductionPage;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class ProductionHashtagUiVerification {

    public static void assertJournalContainsBatch(ProductionPage productionPage, String batchNumber) {
        Allure.step("Перевірити наявність рядка з партією " + batchNumber, () -> {
            assertThat(productionPage.rowWithBatchIsVisible(batchNumber))
                    .as("Рядок з партією %s має бути видимим у таблиці", batchNumber)
                    .isTrue();
        });
    }

    public static void assertNotesContainTag(ProductionPage productionPage,
                                             String batchNumber,
                                             String expectedTag) {
        Allure.step("Перевірити примітки з тегом " + expectedTag, () -> {
            int rowIndex = productionPage.findRowIndexByBatchNumber(batchNumber);
            assertThat(productionPage.getNotesTextForRow(rowIndex))
                    .as("Примітки рядка з партією %s", batchNumber)
                    .contains(expectedTag);
            assertThat(productionPage.getHighlightedTagsForRow(rowIndex))
                    .as("Підсвічені теги (tk-ui #[\\p{L}\\p{N}_-]+)")
                    .contains(expectedTag);
        });
    }

    public static void assertTagFilterShowsOnlyBatches(ProductionPage productionPage,
                                                        ProductionFixture fixture,
                                                        long storageId,
                                                        String tag,
                                                        String productTermForStatsRefresh,
                                                        String... expectedBatches) {
        Allure.step("Перевірити фільтр за тегом " + tag, () -> {
            if (!productionPage.isTagBadgeVisible(tag)) {
                productionPage.refreshTagStatistics(productTermForStatsRefresh);
            }
            productionPage.clickTagFilterBadge(tag);
            assertThat(productionPage.isTagBadgeSelected(tag))
                    .as("Badge тегу %s має бути обраним", tag)
                    .isTrue();

            for (String batch : expectedBatches) {
                assertThat(productionPage.rowWithBatchIsVisible(batch))
                        .as("Після фільтра %s має бути видима партія %s", tag, batch)
                        .isTrue();
            }

            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .tags(List.of(tag))
                    .pageSize(productionPage.getSelectedPageSize())
                    .build();
            List<ManufacturingItemResponse> apiRows = fixture.getJournalPage(query);
            assertThat(apiRows)
                    .as("API має повертати записи для tags=%s", tag)
                    .isNotEmpty();
            for (String batch : expectedBatches) {
                assertThat(apiRows)
                        .anyMatch(item -> batch.equals(item.getBatchNumber()));
            }
        });
    }

    public static void assertBatchAbsentAfterTagFilter(ProductionPage productionPage,
                                                       String tag,
                                                       String excludedBatch) {
        Allure.step("Перевірити, що партія " + excludedBatch + " відсутня після фільтра " + tag, () -> {
            assertThat(productionPage.rowWithBatchIsVisible(excludedBatch))
                    .as("Партія %s не повинна відображатися після фільтра %s", excludedBatch, tag)
                    .isFalse();
        });
    }

    public static void assertNotesMatchApi(ProductionPage productionPage,
                                           ManufacturingItemResponse apiItem) {
        Objects.requireNonNull(apiItem.getBatchNumber(), "batchNumber");
        int rowIndex = productionPage.findRowIndexByBatchNumber(apiItem.getBatchNumber());
        String uiNotes = productionPage.getNotesTextForRow(rowIndex);
        assertThat(uiNotes)
                .as("Примітки UI vs API для production id=%s", apiItem.getId())
                .isEqualTo(apiItem.getNotes() != null ? apiItem.getNotes() : "-");
    }
}
