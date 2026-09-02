package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.FlyPointDashboardPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Route;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: лоадінг дашборду точок взлету (Екіпажі → Точки взлету → Залишки).
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew / Fly Point Inventory UI")
@Story("Fly-point dashboard loading")
public class FlyPointDashboardLoadingUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-fly-load-";
    private static final double ISSUE_AMOUNT = 10.0;
    private static final String SHORT_STATS_ROUTE = "**/api/v1/fly-points/short-stats**";

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClass() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-FLY-LOAD-001")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_LOAD_001)
    @Severity(SeverityLevel.CRITICAL)
    public void sidebarFlyPointStocksFinishesLoadingAndShowsTable() {
        SeededFlyPoint seed = seedFlyPointWithStock("ui-fll-1-");
        injectRoleSession(UserRole.ADMIN, seed.unitId());

        FlyPointDashboardPage dashboard = new FlyPointDashboardPage(page).openViaSidebar();
        dashboard.selectCrewLocation(seed.unitName(), seed.unitId(), true);
        dashboard.openStocksTab().filterByResourceName(seed.resourceName()).expandAllStockCategories();
        dashboard.attachScreenshot("TC-UI-FLY-LOAD-001 — stocks after load");

        assertThat(dashboard.isPageLoadingVisible())
                .as("Спінер «Завантаження...» має зникнути після stocks + short-stats")
                .isFalse();
        assertThat(dashboard.areStockFiltersUsable())
                .as("Фільтри Залишків клікабельні")
                .isTrue();
        assertThat(dashboard.hasStocksTable())
                .as("Таблиця Залишків з колонками Підрозділ / Точка взлету / Ресурс / Кількість / Оновлено")
                .isTrue();
        assertThat(dashboard.stocksTableContains(seed.resourceName())
                || dashboard.hasFlyPointInventoryLink(seed.flyPointId()))
                .as("Тестовий ресурс або лінк точки взлету видимі в таблиці")
                .isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-FLY-LOAD-002")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_LOAD_002)
    @Severity(SeverityLevel.CRITICAL)
    public void stocksTableVisibleWhileShortStatsPending() {
        SeededFlyPoint seed = seedFlyPointWithStock("ui-fll-2-");
        injectRoleSession(UserRole.ADMIN, seed.unitId());

        List<Route> held = new ArrayList<>();
        page.route(SHORT_STATS_ROUTE, held::add);
        try {
            FlyPointDashboardPage dashboard = new FlyPointDashboardPage(page).openViaSidebar();
            dashboard.selectCrewLocation(seed.unitName(), seed.unitId(), false);
            dashboard.attachScreenshot("TC-UI-FLY-LOAD-002 — stocks while short-stats pending");

            assertThat(dashboard.areStockFiltersUsable())
                    .as("Фільтри видимі, поки short-stats pending")
                    .isTrue();
            assertThat(dashboard.isStocksContentSettled())
                    .as("Таблиця або порожній стан Залишків після /stocks, не спінер вкладки")
                    .isTrue();
            assertThat(dashboard.isPageLoadingVisible())
                    .as("Спінер сторінки не перекриває таблицю, поки short-stats ще pending")
                    .isFalse();
        } finally {
            releaseHeldShortStats(held);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-FLY-LOAD-003")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_LOAD_003)
    @Severity(SeverityLevel.NORMAL)
    public void abortShortStatsDoesNotLeaveInfiniteSpinner() {
        SeededFlyPoint seed = seedFlyPointWithStock("ui-fll-3-");
        injectRoleSession(UserRole.ADMIN, seed.unitId());

        page.route(SHORT_STATS_ROUTE, Route::abort);
        try {
            FlyPointDashboardPage dashboard = new FlyPointDashboardPage(page).openViaSidebar();
            dashboard.selectCrewLocation(seed.unitName(), seed.unitId(), false);
            dashboard.assertLoadingHidden();
            dashboard.attachScreenshot("TC-UI-FLY-LOAD-003 — after short-stats abort");

            assertThat(dashboard.isPageLoadingVisible())
                    .as("Abort short-stats не лишає вічний спінер")
                    .isFalse();
            assertThat(dashboard.areStockFiltersUsable())
                    .as("Фільтри Залишків usable після abort short-stats")
                    .isTrue();
            assertThat(dashboard.isStocksContentSettled())
                    .as("Контент Залишків settled після /stocks")
                    .isTrue();
        } finally {
            page.unroute(SHORT_STATS_ROUTE);
        }
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-FLY-LOAD-004")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_LOAD_004)
    @Severity(SeverityLevel.NORMAL)
    public void switchingDashboardSubTabsDoesNotLeaveSpinner() {
        SeededFlyPoint seed = seedFlyPointWithStock("ui-fll-4-");
        injectRoleSession(UserRole.ADMIN, seed.unitId());

        FlyPointDashboardPage dashboard = new FlyPointDashboardPage(page).openViaSidebar();
        dashboard.selectCrewLocation(seed.unitName(), seed.unitId(), true);

        page.waitForResponse(
                r -> r.url().contains("/fly-points/relocations") && "GET".equals(r.request().method()),
                dashboard::openRelocationsTab);
        dashboard.assertLoadingHidden();

        page.waitForResponse(
                r -> r.url().contains("/fly-points/write-offs") && "GET".equals(r.request().method()),
                dashboard::openWriteOffsTab);
        dashboard.assertLoadingHidden();

        page.waitForResponse(
                r -> r.url().contains("/fly-points/turn-over") && "GET".equals(r.request().method()),
                dashboard::openTurnOverTab);
        dashboard.assertLoadingHidden();

        page.waitForResponse(
                r -> r.url().contains("/fly-points/stocks") && "GET".equals(r.request().method()),
                dashboard::openStocksTab);
        dashboard.assertLoadingHidden();
        dashboard.attachScreenshot("TC-UI-FLY-LOAD-004 — back on stocks");

        assertThat(dashboard.isPageLoadingVisible()).isFalse();
        assertThat(dashboard.isStocksContentSettled())
                .as("Після повернення на Залишки таблиця знову видима")
                .isTrue();
    }

    private SeededFlyPoint seedFlyPointWithStock(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario(prefix);
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.flyPoint().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        return new SeededFlyPoint(
                scenario.unit().getId(),
                scenario.unit().getName(),
                scenario.flyPoint().getId(),
                normalizeName(resource.getName()));
    }

    private void releaseHeldShortStats(List<Route> held) {
        for (Route route : held) {
            try {
                route.fulfill(new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody("[]"));
            } catch (RuntimeException e) {
                log.debug("Could not fulfill held short-stats: {}", e.getMessage());
            }
        }
        try {
            page.unroute(SHORT_STATS_ROUTE);
        } catch (RuntimeException e) {
            log.debug("Could not unroute short-stats: {}", e.getMessage());
        }
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
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

    private record SeededFlyPoint(long unitId, String unitName, long flyPointId, String resourceName) {
    }
}
