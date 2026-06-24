package com.erp.utils.helpers;

import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.common.RelocationJournalRow;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.RelocationPage;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class RelocationJournalUiVerification {

    public static void assertFilteredRowsContainMarkers(RelocationPage relocationPage,
                                                       List<String> rowTextMarkers) {
        Allure.step("Перевірити, що відфільтрований журнал містить тестові переміщення", () -> {
            assertThat(relocationPage.isJournalLoadErrorVisible())
                    .as("Помилка завантаження журналу не повинна відображатися")
                    .isFalse();
            for (String marker : rowTextMarkers) {
                assertThat(relocationPage.isRowWithTextVisible(marker))
                        .as("Рядок з текстом «%s» має бути видимим після фільтрації", marker)
                        .isTrue();
            }
        });
    }

    public static void assertFilteredRowsContainRelocationIds(RelocationFixture fixture,
                                                              RelocationJournalQuery query,
                                                              UserRole role,
                                                              List<Long> relocationIds) {
        List<RelocationResponse> apiPage = fixture.getJournalPage(query, role);
        for (Long id : relocationIds) {
            assertThat(apiPage)
                    .as("API має повертати переміщення id=%s у відфільтрованому списку", id)
                    .anyMatch(r -> id.equals(r.getId()));
        }
    }

    public static void assertDisplayedOrderMatchesApi(RelocationPage relocationPage,
                                                      RelocationFixture fixture,
                                                      RelocationJournalQuery query,
                                                      UserRole role,
                                                      String stepLabel) {
        Allure.step(stepLabel, () -> {
            int pageSize = relocationPage.getSelectedPageSize();
            RelocationJournalQuery pagedQuery = query.toBuilder().pageSize(pageSize).build();

            List<RelocationResponse> apiPage = fixture.getJournalPage(pagedQuery, role);
            List<RelocationJournalRow> uiRows = relocationPage.getDisplayedJournalRows();

            assertThat(relocationPage.isJournalLoadErrorVisible())
                    .as("Помилка завантаження журналу не повинна відображатися")
                    .isFalse();

            long totalElements = fixture.getJournalTotalElements(pagedQuery, role);
            int expectedRowCount = (int) Math.min(pageSize, totalElements);

            assertThat(uiRows)
                    .as("Кількість рядків UI на першій сторінці")
                    .hasSize(expectedRowCount);

            List<String> uiRecipients = uiRows.stream()
                    .map(RelocationJournalRow::getRecipientName)
                    .collect(Collectors.toList());
            List<String> apiRecipients = apiPage.stream()
                    .map(r -> r.getRecipient() != null ? r.getRecipient().getName() : null)
                    .collect(Collectors.toList());

            assertThat(uiRecipients)
                    .as("Порядок колонки «До» на UI має збігатися з API")
                    .isEqualTo(apiRecipients);

            Allure.parameter("totalElements", totalElements);
            Allure.parameter("displayedRows", uiRows.size());
            Allure.parameter("sort", pagedQuery.toQueryParams().get("sort"));
        });
    }

    public static void assertMarkersPresentInApiPage(List<RelocationResponse> apiPage,
                                                     List<Long> relocationIds) {
        for (Long id : relocationIds) {
            assertThat(apiPage)
                    .as("API-сторінка має містити переміщення id=%s", id)
                    .anyMatch(r -> id.equals(r.getId()));
        }
    }
}
