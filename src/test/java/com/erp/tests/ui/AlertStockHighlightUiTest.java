package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertStockHighlightUiTest extends BaseUITest {

    private AlertFixture alertFixture;
    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        alertFixture = new AlertFixture(testContext, apiExecutor);
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
    @TestCaseId("TC-UI-ALERT-001")
    @Story("Inventory status badges")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            tk-ui InventoryPage.getStatusBadge: 100 → «відсутній» / destructive,
            60 → «менше норми» / warning, 40 → «достатньо» / green; підпис (мін. N).
            Ресурс без порогу — без цих бейджів.
            """)
    public void statusBadgesOnInventoryPage() {
        OpenedInventory opened = openHighlightedInventory();
        AlertFixture.StockHighlightSeed seed = opened.seed();
        UnitManagementPage stock = opened.page();

        assertThat(stock.hasStatusBadge(seed.red().getName(), UnitManagementPage.BADGE_ABSENT))
                .as("бейдж «відсутній»").isTrue();
        assertThat(stock.statusBadgeVariant(seed.red().getName(), UnitManagementPage.BADGE_ABSENT))
                .isEqualTo("destructive");

        assertThat(stock.hasStatusBadge(seed.yellow().getName(), UnitManagementPage.BADGE_BELOW_NORM))
                .as("бейдж «менше норми»").isTrue();
        assertThat(stock.statusBadgeVariant(seed.yellow().getName(), UnitManagementPage.BADGE_BELOW_NORM))
                .isEqualTo("warning");

        assertThat(stock.hasStatusBadge(seed.green().getName(), UnitManagementPage.BADGE_ENOUGH))
                .as("бейдж «достатньо»").isTrue();
        assertThat(stock.statusBadgeVariant(seed.green().getName(), UnitManagementPage.BADGE_ENOUGH))
                .isEqualTo("green");

        String yellowLocation = stock.getLocationCellText(seed.yellow().getName());
        assertThat(yellowLocation).contains("мін.");
        assertThat(yellowLocation).contains(String.valueOf((int) AlertFixture.DEFAULT_LIMIT));

        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_ABSENT)).isFalse();
        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_BELOW_NORM)).isFalse();
        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_ENOUGH)).isFalse();

        stock.attachScreenshot("TC-UI-ALERT-001 — status badges");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-ALERT-002")
    @Story("Pin to top by weight")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Назви aaa-ok / bbb-plain / mmm-low / zzz-out — алфавітно green перший.
            Очікування продукту: red і yellow вище green і plain (weight DESC перший ключ).

            Відомий дефект (dev, 2026-09-05): InventoryPage.getPageByHierarchy(..., ['name,asc']).
            Фактично green index=0, red index=3. Тест червоний до фіксу в tk-ui —
            очікування навмисно не послаблюємо.
            """)
    public void alertedRowsPinnedAboveNameSort() {
        OpenedInventory opened = openHighlightedInventory();
        AlertFixture.StockHighlightSeed seed = opened.seed();
        UnitManagementPage stock = opened.page();

        int redIdx = stock.indexOfResource(seed.red().getName());
        int yellowIdx = stock.indexOfResource(seed.yellow().getName());
        int greenIdx = stock.indexOfResource(seed.green().getName());
        int plainIdx = stock.indexOfResource(seed.plain().getName());
        assertThat(List.of(redIdx, yellowIdx, greenIdx, plainIdx))
                .as("усі 4 ресурси видимі після пошуку")
                .allMatch(idx -> idx >= 0);
        assertThat(redIdx).as("red вище green").isLessThan(greenIdx);
        assertThat(redIdx).as("red вище plain").isLessThan(plainIdx);
        assertThat(yellowIdx).as("yellow вище green").isLessThan(greenIdx);
        assertThat(yellowIdx).as("yellow вище plain").isLessThan(plainIdx);

        stock.attachScreenshot("TC-UI-ALERT-002 — pin-to-top");
    }

    private OpenedInventory openHighlightedInventory() {
        AlertFixture.StockHighlightSeed seed = alertFixture.seedHighlightScenario(
                storageFixture, resourceFixture, relocationFixture, inventoryFixture);
        injectRoleSession(UserRole.ADMIN, seed.storage().getId());
        page = browserContext.newPage();
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(seed.storage().getId())
                .waitForLoaded()
                .searchAndWaitForResource(seed.searchToken(), seed.red().getName());
        return new OpenedInventory(seed, stock);
    }

    private record OpenedInventory(AlertFixture.StockHighlightSeed seed, UnitManagementPage page) {}

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
}
