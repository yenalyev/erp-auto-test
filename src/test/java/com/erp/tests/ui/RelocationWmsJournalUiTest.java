package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: журнал Видати/Отримати — фільтри Продукт/Обладнання та стабільність пагінації.
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
@Story("WMS journal filters and pagination")
public class RelocationWmsJournalUiTest extends BaseUITest {

    private static final String RECEIVED_TABLE_ID = "received-relocations";
    private static final String RESOURCE_PREFIX = "ui-wms-flt-";

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private EquipmentFixture equipmentFixture;

    private long storageId;
    private ResourceResponse activeResource;
    private ResourceResponse archivedResource;
    private EquipmentResponse equipment;
    private Long archivedResourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);

        relocationFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        equipmentFixture.prepareCategoryContext();

        storageId = ConfigProvider.getOwner1StorageId();
        activeResource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "act-");
        archivedResource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "arch-");
        archivedResourceId = archivedResource.getId();

        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        equipment = equipmentFixture.createEquipmentOnStorage(UserRole.ADMIN, storageId, categoryId);

        injectOwner1Session(RelocationJournalQuery.DEFAULT_UI_PAGE_SIZE);
    }

    @AfterClass(alwaysRun = true)
    public void reactivateArchivedResource() {
        if (archivedResourceId != null) {
            try {
                resourceFixture.unarchive(UserRole.ADMIN, archivedResourceId);
            } catch (Exception e) {
                log.warn("Failed to unarchive resource {}: {}", archivedResourceId, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-005")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-WMS-006 AC-04 / CPMA-430: фільтри «Продукт» (активні+архівні) та «Обладнання»
            на сторінці Видати/Отримати дозволяють обрати значення й застосувати фільтрацію.
            """)
    public void productAndEquipmentFiltersAcceptActiveArchivedAndEquipment() {
        assertThat(resourceFixture.deactivate(UserRole.ADMIN, archivedResourceId).statusCode())
                .as("deactivate archived resource")
                .isEqualTo(200);
        assertThat(resourceFixture.getPage(UserRole.ADMIN, false, shortName(archivedResource.getName()))
                        .stream()
                        .map(ResourceResponse::getId))
                .as("деактивований ресурс у словнику (isActive=false) — джерело для фільтра «Продукт»")
                .contains(archivedResourceId);

        RelocationPage journal = new RelocationPage(page).open().waitForJournalDataSettled();
        journal.attachScreenshot("TC-WMS-005 — journal filters initial");

        assertThat(journal.isProductFilterVisible()).as("Фільтр «Продукт» видимий").isTrue();
        assertThat(journal.isEquipmentFilterVisible()).as("Фільтр «Обладнання» видимий").isTrue();

        journal.filterByProduct(shortName(activeResource.getName()));
        assertThat(journal.isJournalLoadErrorVisible()).as("Після фільтра активного продукту").isFalse();
        journal.attachScreenshot("TC-WMS-005 — active product filter");

        journal = new RelocationPage(page).open().waitForJournalDataSettled();
        journal.filterByEquipment(shortName(equipment.getName()));
        assertThat(journal.isJournalLoadErrorVisible()).as("Після фільтра обладнання").isFalse();
        journal.attachScreenshot("TC-WMS-005 — equipment filter");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-006")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-WMS-006 AC-03 / CPMA-463: на табі «Отримано» перші N елементів при pageSize=10
            збігаються з першими N при pageSize=20 (порядок стабільний).
            """)
    public void receivedTabPaginationKeepsStableFirstRows() {
        ensureReceivedRowsAtLeast(12);

        page.evaluate("localStorage.setItem('"
                + RelocationPage.pageSizeStorageKey(RECEIVED_TABLE_ID) + "', '10')");
        RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
        assertThat(journal.isJournalLoadErrorVisible()).isFalse();
        List<String> firstPage = journal.getDisplayedRowIdentityTexts();
        assertThat(firstPage)
                .as("При pageSize=10 має бути хоча б один рядок на «Отримано»")
                .isNotEmpty();
        int compareCount = Math.min(10, firstPage.size());
        List<String> expectedPrefix = new ArrayList<>(firstPage.subList(0, compareCount));
        journal.attachScreenshot("TC-WMS-006 — pageSize 10");

        page.evaluate("localStorage.setItem('"
                + RelocationPage.pageSizeStorageKey(RECEIVED_TABLE_ID) + "', '20')");
        journal = new RelocationPage(page).open().openReceivedTab();
        List<String> largerPage = journal.getDisplayedRowIdentityTexts();
        journal.attachScreenshot("TC-WMS-006 — pageSize 20");

        assertThat(largerPage.size())
                .as("pageSize=20 показує не менше рядків, ніж pageSize=10")
                .isGreaterThanOrEqualTo(compareCount);
        assertThat(largerPage.subList(0, compareCount))
                .as("Перші %d рядків при pageSize=20 = pageSize=10", compareCount)
                .isEqualTo(expectedPrefix);
    }

    private void ensureReceivedRowsAtLeast(int minRows) {
        RelocationPage probe = new RelocationPage(page).open().openReceivedTab();
        int current = probe.getDisplayedRowCount();
        if (current >= minRows) {
            return;
        }
        int toCreate = minRows - current + 2;
        Long resourceId = activeResource.getId();
        for (int i = 0; i < toCreate; i++) {
            String batch = RelocationDataFactory.uniqueBatchNumber();
            relocationFixture.createExternalReceive(UserRole.OWNER_1, storageId, resourceId, 1.0, batch);
        }
    }

    private void injectOwner1Session(int pageSize) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');"
                        + "localStorage.setItem('"
                        + RelocationPage.pageSizeStorageKey(RECEIVED_TABLE_ID) + "', '"
                        + pageSize + "');");
    }

    private static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
