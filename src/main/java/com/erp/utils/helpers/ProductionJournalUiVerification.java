package com.erp.utils.helpers;

import com.erp.fixtures.ProductionFixture;
import com.erp.models.common.ProductionJournalRow;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.pages.ProductionPage;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class ProductionJournalUiVerification {

    public static void assertJournalMatchesApi(ProductionPage productionPage,
                                               ProductionFixture fixture,
                                               ProductionJournalQuery query,
                                               ManufacturingItemResponse anchor,
                                               String stepLabel) {
        Objects.requireNonNull(anchor, "anchor production item");

        Allure.step(stepLabel, () -> {
            int pageSize = productionPage.getSelectedPageSize();
            ProductionJournalQuery pagedQuery = query.toBuilder().pageSize(pageSize).build();

            List<ManufacturingItemResponse> apiPage = fixture.getJournalPage(pagedQuery);
            long totalElements = fixture.getJournalTotalElements(pagedQuery);
            int expectedRowCount = (int) Math.min(pageSize, totalElements);

            List<ProductionJournalRow> displayedRows = productionPage.getDisplayedJournalRows();
            ProductionJournalRow expectedAnchor = ProductionJournalRow.fromApi(anchor);

            assertThat(productionPage.isJournalLoadErrorVisible())
                    .as("Помилка завантаження журналу не повинна відображатися")
                    .isFalse();

            if (totalElements == 0) {
                assertThat(productionPage.isEmptyStateVisible())
                        .as("Порожній стан має відображатися, коли API повертає 0 записів")
                        .isTrue();
                assertThat(displayedRows).isEmpty();
            } else {
                assertThat(productionPage.isEmptyStateVisible())
                        .as("Порожній стан не повинен відображатися, коли API повертає записи")
                        .isFalse();
                assertThat(displayedRows)
                        .as("Кількість рядків UI на першій сторінці")
                        .hasSize(expectedRowCount);

                assertThat(apiPage)
                        .as("Опорний запис id=%s має бути на поточній сторінці API", anchor.getId())
                        .anyMatch(item -> Objects.equals(item.getId(), anchor.getId()));

                ProductionJournalAssertions.assertAnchorRowMatchesApi(expectedAnchor, displayedRows);
            }

            Allure.parameter("anchorProductionId", anchor.getId());
            Allure.parameter("anchorBatchNumber", anchor.getBatchNumber());
            Allure.parameter("totalElements", totalElements);
            Allure.parameter("displayedRows", displayedRows.size());
        });
    }
}
