package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.RelocationJournalUiVerification;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Relocation journal — filter and sort UI")
public class RelocationJournalFilterSortUITest extends BaseUITest {

    private static final String SENT_RELOCATIONS_TABLE_ID = "sent-relocations";

    private RelocationFixture fixture;
    private long storageId;
    private long owner2StorageId;
    private Long unitStorageId;
    private Long resourceId;
    private String resourceName;
    private String productSearchTerm;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        unitStorageId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        ResourceResponse resource = resources.get(0);
        resourceName = resource.getName();
        productSearchTerm = resourceName.length() >= 4
                ? resourceName.substring(0, Math.min(resourceName.length(), 8))
                : resourceName;

        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');"
                        + "localStorage.setItem('"
                        + RelocationPage.pageSizeStorageKey(SENT_RELOCATIONS_TABLE_ID) + "', '"
                        + RelocationJournalQuery.DEFAULT_UI_PAGE_SIZE + "');");
    }

    @Test
    @TestCaseId({
            "TC-UI-REL-009",
            "TC-WMS-007"
    })
    @Story("Filter and sort on sent history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Видано: фільтр за продуктом + сортування за «До».
            Два нові переміщення (різні отримувачі) мають потрапити у відфільтрований список
            у тому ж порядку, що й GET /relocations.
            """)
    public void sentHistoryProductFilterAndRecipientSort() {
        long timestamp = System.currentTimeMillis();
        String markerToOwner2 = "ui-rel-flt-sort-A-" + timestamp;
        String markerToUnit = "ui-rel-flt-sort-B-" + timestamp;
        double amount = 3.0;

        fixture.ensureStock(storageId, resourceId, 50.0);

        RelocationResponse sendToOwner2 = Allure.step("API: видача на owner2 storage", () ->
                fixture.createSendWithDescription(
                        UserRole.OWNER_1, storageId, owner2StorageId, resourceId, amount, markerToOwner2));

        Allure.step("API: підтвердити видачу (FINISHED)", () ->
                fixture.resolve(
                        UserRole.OWNER_2, sendToOwner2.getId(), owner2StorageId,
                        RelocationState.FINISHED, markerToOwner2));

        RelocationResponse sendToUnit = Allure.step("API: видача на UNIT (AUTO_FINISHED)", () ->
                fixture.createSendWithDescription(
                        UserRole.OWNER_1, storageId, unitStorageId, resourceId, amount, markerToUnit));

        String recipientOwner2 = sendToOwner2.getRecipient().getName();
        String recipientUnit = sendToUnit.getRecipient().getName();
        Allure.parameter("markerToOwner2", markerToOwner2);
        Allure.parameter("markerToUnit", markerToUnit);
        Allure.parameter("recipientOwner2", recipientOwner2);
        Allure.parameter("recipientUnit", recipientUnit);
        Allure.parameter("productSearchTerm", productSearchTerm);

        RelocationPage relocationPage = Allure.step("Відкрити журнал переміщень → Видано", () -> {
            RelocationPage journal = new RelocationPage(page).open().openSentTab();
            assertThat(journal.isJournalLoadErrorVisible())
                    .as("Журнал не повинен показувати помилку завантаження")
                    .isFalse();
            journal.attachScreenshot("TC-UI-REL-009 — sent tab initial");
            return journal;
        });

        Allure.step("Застосувати фільтр продукту «" + productSearchTerm + "»", () -> {
            relocationPage.filterByProduct(productSearchTerm);
            relocationPage.attachScreenshot("TC-UI-REL-009 — product filter applied");
        });

        RelocationJournalQuery filteredQuery = RelocationJournalQuery.sentHistoryUi(storageId)
                .toBuilder()
                .productId(resourceId)
                .build();

        Allure.step("Перевірити, що тестові переміщення видимі після фільтрації", () -> {
            RelocationJournalUiVerification.assertFilteredRowsContainMarkers(
                    relocationPage, List.of(recipientOwner2, recipientUnit));
            RelocationJournalUiVerification.assertFilteredRowsContainRelocationIds(
                    fixture, filteredQuery, UserRole.OWNER_1,
                    List.of(sendToOwner2.getId(), sendToUnit.getId()));
        });

        Allure.step("Сортувати за колонкою «До» (recipient.name ASC)", () -> {
            relocationPage.sortByColumn("До");
            relocationPage.attachScreenshot("TC-UI-REL-009 — sorted by recipient ASC");
        });

        RelocationJournalQuery sortedQuery = filteredQuery.toBuilder()
                .sortField(RelocationJournalQuery.SortField.RECIPIENT_NAME)
                .build();

        RelocationJournalUiVerification.assertDisplayedOrderMatchesApi(
                relocationPage,
                fixture,
                sortedQuery,
                UserRole.OWNER_1,
                "UI-таблиця «Видано» має збігатися з API після фільтра продукту та сорту за «До»");
    }
}
