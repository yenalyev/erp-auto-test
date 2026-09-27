package com.erp.utils.helpers;

import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.common.RelocationJournalRow;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.RelocationPage;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        int pageSize = query.getPageSize();
        RelocationJournalQuery firstPageQuery = query.toBuilder().page(0).build();
        long totalElements = fixture.getJournalTotalElements(firstPageQuery, role);
        int pageCount = (int) Math.ceil((double) totalElements / pageSize);
        Set<Long> actualIds = new HashSet<>();
        int pagesChecked = 0;

        for (int page = 0; page < pageCount && !actualIds.containsAll(relocationIds); page++) {
            fixture.getJournalPage(query.toBuilder().page(page).build(), role).stream()
                    .map(RelocationResponse::getId)
                    .forEach(actualIds::add);
            pagesChecked++;
        }

        for (Long id : relocationIds) {
            assertThat(actualIds)
                    .as("API має повертати переміщення id=%s у повному відфільтрованому списку", id)
                    .contains(id);
        }

        Allure.parameter("filteredTotalElements", totalElements);
        Allure.parameter("filteredPagesChecked", pagesChecked);
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
                    .map(row -> normalizeName(row.getRecipientName()))
                    .collect(Collectors.toList());
            List<String> apiRecipients = apiPage.stream()
                    .map(r -> normalizeName(r.getRecipient() != null ? r.getRecipient().getName() : null))
                    .collect(Collectors.toList());

            assertThat(uiRecipients)
                    .as("Порядок колонки «До» на UI має збігатися з API")
                    .isEqualTo(apiRecipients);

            Allure.parameter("totalElements", totalElements);
            Allure.parameter("displayedRows", uiRows.size());
            Allure.parameter("sort", pagedQuery.toQueryParams().get("sort"));
        });
    }

    /**
     * Storage names may carry surrounding whitespace in the API payload while the DOM renders
     * them collapsed, so both sides are normalised before comparing the displayed order.
     */
    private static String normalizeName(String name) {
        return name != null ? name.trim().replaceAll("\\s+", " ") : null;
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
