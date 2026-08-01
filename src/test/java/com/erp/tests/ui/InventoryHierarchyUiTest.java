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
import com.erp.test_context.ContextKey;
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
    private Long resourceId;
    private Long childOnlyResourceId;

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
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        childOnlyResourceId = resourceFixture.createUniqueResource("ui-hier-exp-res-").getId();
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
            INTERNAL parent: checkbox «По всій ієрархії» visible unchecked → single table («Статус»).
            Enable → multi table («Локація»), write actions disabled.
            «Всі локації» → checkbox hidden.
            """)
    public void hierarchyCheckboxTogglesMultiTable() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-hier-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-hier-c-");
        relocationFixture.ensureStock(parent.getId(), resourceId, 8.0);
        relocationFixture.ensureStock(child.getId(), resourceId, 4.0);

        injectRoleSession(UserRole.ADMIN, parent.getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(parent.getId())
                .waitForLoaded();

        assertThat(stock.isHierarchyCheckboxVisible()).isTrue();
        assertThat(stock.isHierarchyCheckboxChecked()).isFalse();
        assertThat(stock.isSingleLocationStatusColumnVisible()).isTrue();
        stock.attachScreenshot("TC-WMS-007-013 — single before hierarchy");

        stock.enableHierarchyView();
        assertThat(stock.isHierarchyCheckboxChecked()).isTrue();
        assertThat(stock.isMultiLocationTableVisible()).isTrue();
        assertThat(stock.isConductInventoryButtonEnabled())
                .as("Conduct must be disabled in hierarchy view")
                .isFalse();
        stock.attachScreenshot("TC-WMS-007-013 — hierarchy multi table");

        stock.disableHierarchyView();
        assertThat(stock.isHierarchyCheckboxChecked()).isFalse();
        assertThat(stock.isSingleLocationStatusColumnVisible()).isTrue();

        injectAllLocationsSession(UserRole.ADMIN);
        page = browserContext.newPage();
        UnitManagementPage allLocs = new UnitManagementPage(page).openForAllLocations().waitForLoaded();
        assertThat(allLocs.isHierarchyCheckboxVisible())
                .as("Hierarchy checkbox must be hidden for «Всі локації»")
                .isFalse();
        assertThat(allLocs.isMultiLocationTableVisible()).isTrue();
        allLocs.attachScreenshot("TC-WMS-007-013 — all locations no checkbox");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-007-015")
    @Story("EXTERNAL forces hierarchy and blocks conduct")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            EXTERNAL: checkbox «По всій ієрархії» checked+disabled; multi table;
            conduct blocked (supported=false).
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

        assertThat(stock.isHierarchyCheckboxVisible()).isTrue();
        assertThat(stock.isHierarchyCheckboxChecked()).isTrue();
        assertThat(stock.isHierarchyCheckboxEnabled()).isFalse();
        assertThat(stock.isMultiLocationTableVisible()).isTrue();
        assertThat(stock.isConductInventoryButtonEnabled())
                .as("Conduct must be disabled when supported=false")
                .isFalse();
        stock.attachScreenshot("TC-WMS-007-015 — EXTERNAL forced hierarchy");
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-007-017")
    @Story("Excel export with and without hierarchy checkbox")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Без «По всій ієрархії»: Експорт в Excel качає непорожній XLSX.
            З увімкненим чекбоксом: кнопка лишається enabled (на відміну від conduct);
            файл також качається, але контент = лише selected storage
            (GET export-remainder?storageId=parent) — child-only resource у файлі відсутній.
            """)
    public void exportExcelWithAndWithoutHierarchyCheckbox() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-exp-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-exp-c-");
        relocationFixture.ensureStock(parent.getId(), resourceId, 9.0);
        relocationFixture.ensureStock(child.getId(), childOnlyResourceId, 6.0);

        ResourceResponse childRes = resourceFixture.getById(UserRole.ADMIN, childOnlyResourceId);
        String childName = childRes.getName();

        injectRoleSession(UserRole.ADMIN, parent.getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(parent.getId())
                .waitForLoaded();

        Allure.step("Експорт без hierarchy", () -> {
            assertThat(stock.isHierarchyCheckboxChecked()).isFalse();
            assertThat(stock.isExportToExcelButtonEnabled()).isTrue();
            UnitManagementPage.ExportDownloadResult download = stock.clickExportToExcelAndDownload();
            UiDownloadAssertions.assertNonEmptyXlsx(
                    download.path(), download.sizeBytes(), "Export without hierarchy");
            assertThat(XlsxContentAssertions.zipContainsText(download.path(), childName))
                    .as("Single-location export must not contain child-only «%s»", childName)
                    .isFalse();
            stock.attachScreenshot("TC-WMS-007-017 — export without hierarchy");
        });

        Allure.step("Експорт з «По всій ієрархії»", () -> {
            stock.enableHierarchyView();
            assertThat(stock.isHierarchyCheckboxChecked()).isTrue();
            assertThat(stock.isExportToExcelButtonEnabled())
                    .as("Export stays enabled in hierarchy multi-view (unlike conduct)")
                    .isTrue();
            UnitManagementPage.ExportDownloadResult download = stock.clickExportToExcelAndDownload();
            UiDownloadAssertions.assertNonEmptyXlsx(
                    download.path(), download.sizeBytes(), "Export with hierarchy checkbox");
            assertThat(XlsxContentAssertions.zipContainsText(download.path(), childName))
                    .as("Hierarchy checkbox does not change export API — child-only «%s» still absent",
                            childName)
                    .isFalse();
            stock.attachScreenshot("TC-WMS-007-017 — export with hierarchy");
        });
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
