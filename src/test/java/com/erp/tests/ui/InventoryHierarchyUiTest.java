package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.erp.utils.helpers.XlsxContentAssertions;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPMA-674 / CPMA-675 UI: «По всій ієрархії», EXTERNAL forced hierarchy.
 */
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock Hierarchy UI")
public class InventoryHierarchyUiTest extends BaseUITest {

    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupStoragesAfterMethod() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStoragesAfterClass() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-007-013")
    @Story("Hierarchy checkbox toggles multi-location table")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            INTERNAL parent: tk-ui завжди вантажить залишки через getPageByHierarchy
            (колонка «Локація», чекбокса «По всій ієрархії» більше немає).
            Провести інвентаризацію disabled, поки сесія закрита.
            «Всі локації» → multi table без кнопки відкриття сесії на конкретний склад.
            """)
    public void hierarchyCheckboxTogglesMultiTable() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-hier-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-hier-c-");
        long stockResourceId = resourceFixture.createUniqueResource("ui-hier-res-").getId();
        relocationFixture.seedExactStock(parent.getId(), stockResourceId, 8.0);
        relocationFixture.seedExactStock(child.getId(), stockResourceId, 4.0);

        injectRoleSession(UserRole.ADMIN, parent.getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(parent.getId())
                .waitForLoaded();

        assertThat(stock.isHierarchyCheckboxVisible())
                .as("Чекбокс «По всій ієрархії» прибрано з /inventory")
                .isFalse();
        assertThat(stock.isMultiLocationTableVisible())
                .as("Обрана parent-локація: таблиця ієрархії (колонка «Локація»)")
                .isTrue();
        assertThat(stock.isConductInventoryButtonEnabled())
                .as("Conduct disabled while inventory session is closed")
                .isFalse();
        stock.attachScreenshot("TC-WMS-007-013 — hierarchy table on parent");

        injectAllLocationsSession(UserRole.ADMIN);
        page = browserContext.newPage();
        UnitManagementPage allLocs = new UnitManagementPage(page).openForAllLocations().waitForLoaded();
        assertThat(allLocs.isHierarchyCheckboxVisible())
                .as("Hierarchy checkbox must stay hidden for «Всі локації»")
                .isFalse();
        assertThat(allLocs.isMultiLocationTableVisible()).isTrue();
        allLocs.attachScreenshot("TC-WMS-007-013 — all locations no checkbox");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-007-015")
    @Story("EXTERNAL forces hierarchy and blocks conduct")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            EXTERNAL: tk-ui завжди ієрархічна таблиця (колонка «Локація»);
            чекбокса немає; conduct blocked (supported=false).
            """)
    public void externalForcesHierarchyAndBlocksConduct() {
        StorageResponse external = storageFixture.createExternalChildStorage(
                storageFixture.resolveParentUnit().getId(), "ui-hier-ext-");

        assertThat(inventoryFixture.getStatus(external.getId(), UserRole.ADMIN).isSupported())
                .isFalse();

        injectRoleSession(UserRole.ADMIN, external.getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(external.getId())
                .waitForLoaded();

        assertThat(stock.isHierarchyCheckboxVisible())
                .as("Чекбокс «По всій ієрархії» прибрано з /inventory")
                .isFalse();
        assertThat(stock.isMultiLocationTableVisible()).isTrue();
        assertThat(stock.isConductInventoryButtonEnabled())
                .as("Conduct must be disabled when supported=false")
                .isFalse();
        stock.attachScreenshot("TC-WMS-007-015 — EXTERNAL hierarchy table");
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-007-017")
    @Story("Excel export from hierarchy inventory page")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            /inventory завжди ієрархія: «Експорт в Excel» качає непорожній XLSX
            (exportRemaindersHierarchy з parentStorageId).
            """)
    public void exportExcelWithAndWithoutHierarchyCheckbox() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-exp-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-exp-c-");
        long parentResId = resourceFixture.createUniqueResource("ui-exp-p-res-").getId();
        ResourceResponse childRes = resourceFixture.createUniqueResource("ui-exp-c-res-");
        relocationFixture.seedExactStock(parent.getId(), parentResId, 9.0);
        relocationFixture.seedExactStock(child.getId(), childRes.getId(), 6.0);

        String childName = childRes.getName();

        injectRoleSession(UserRole.ADMIN, parent.getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(parent.getId())
                .waitForLoaded();

        assertThat(stock.isHierarchyCheckboxVisible()).isFalse();
        assertThat(stock.isExportToExcelButtonEnabled()).isTrue();
        UnitManagementPage.ExportDownloadResult download = stock.clickExportToExcelAndDownload();
        UiDownloadAssertions.assertNonEmptyXlsx(
                download.path(), download.sizeBytes(), "Hierarchy inventory export");
        assertThat(XlsxContentAssertions.zipContainsText(download.path(), childName))
                .as("Hierarchy export includes child-only «%s»", childName)
                .isTrue();
        stock.attachScreenshot("TC-WMS-007-017 — hierarchy export");
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    private void injectAllLocationsSession(UserRole role) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
    }
}
