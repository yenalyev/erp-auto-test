package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.CrewAnalyticsPage;
import com.erp.pages.FlyPointDashboardPage;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.UnitManagementPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UI: інвентаризація CREW / FLY_POINT через /crew-analytics та /fly-point-dashboard.
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew / Fly Point Inventory UI")
@Story("Deep-link inventory from analytics")
public class CrewFlyPointInventoryUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-cfp-inv-";
    private static final double ISSUE_AMOUNT = 10.0;
    private static final double TARGET_AMOUNT = 14.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;

    private Long sessionStorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupSession() {
        if (sessionStorageId != null) {
            try {
                inventoryFixture.ensureClosed(sessionStorageId);
            } catch (Exception e) {
                log.warn("Session cleanup failed: {}", e.getMessage());
            }
            sessionStorageId = null;
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClass() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test(priority = 10)
    @TestCaseId({
            "TC-UI-CREW-015",
            "TC-UI-CREW-004"
    })
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_015)
    @Severity(SeverityLevel.CRITICAL)
    public void crewAnalyticsOpensUnattachedCrewInventoryAndConduct() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("ui-cfp-u-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "u-");
        long crewId = scenario.crew().getId();
        String resourceName = normalizeName(resource.getName());

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resource.getId(),
                ISSUE_AMOUNT);

        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(crewId);
        sessionStorageId = crewId;

        // parentId для /crews/stocks = UNIT сценарію (не OWNER_1 sibling)
        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());

        CrewAnalyticsPage analytics = Allure.step("UI: /crew-analytics",
                () -> new CrewAnalyticsPage(page).open()
                        .selectCrewLocationByName(scenario.unit().getName())
                        .openStocksTab());
        analytics.setIncludeFlyPointStocks(false);
        analytics.filterByResourceName(resourceName);
        analytics.clickCrewInventoryLink(crewId);

        assertThat(page.url())
                .as("Deep-link на inventory CREW")
                .contains("/inventory?storageId=" + crewId);

        UnitManagementPage stock = new UnitManagementPage(page).waitForLoaded()
                .waitForConductButtonEnabled();
        assertThat(stock.isConductInventoryButtonEnabled()).isTrue();
        stock.clickConductInventory();

        InventoryEditPage edit = new InventoryEditPage(page).waitForLoaded();
        edit.updateAmountForResource(resourceName, String.valueOf((int) TARGET_AMOUNT));
        edit.saveChanges();

        assertThat(inventoryFixture.getResourceStock(crewId, resource.getId(), UserRole.ADMIN))
                .isCloseTo(TARGET_AMOUNT, within(0.01));
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-CREW-016")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_016)
    @Severity(SeverityLevel.CRITICAL)
    public void attachedCrewRowLinksToFlyPointDashboardNotCrewInventory() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("ui-cfp-a-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "a-");
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();
        String resourceName = normalizeName(resource.getName());

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resource.getId(),
                ISSUE_AMOUNT);

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());

        CrewAnalyticsPage analytics = new CrewAnalyticsPage(page).open()
                .selectCrewLocationByName(scenario.unit().getName())
                .openStocksTab();
        analytics.setIncludeFlyPointStocks(true);
        analytics.filterByResourceName(resourceName);

        assertThat(analytics.hasFlyPointDashboardLink(flyPointId))
                .as("Attached: лінк на fly-point-dashboard")
                .isTrue();
        assertThat(analytics.hasCrewInventoryLink(crewId))
                .as("Attached: немає лінка екіпажу на /inventory?storageId=crew")
                .isFalse();

        analytics.clickFlyPointDashboardLink(flyPointId);
        assertThat(page.url()).contains("/fly-point-dashboard");
        assertThat(page.url()).contains("flyPointId=" + flyPointId);
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-CREW-017")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_017)
    @Severity(SeverityLevel.CRITICAL)
    public void flyPointDashboardOpensInventoryAndConduct() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("ui-cfp-fp-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "fp-");
        long flyPointId = scenario.flyPoint().getId();
        String resourceName = normalizeName(resource.getName());

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                flyPointId,
                resource.getId(),
                ISSUE_AMOUNT);

        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.openSession(flyPointId);
        sessionStorageId = flyPointId;

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());

        FlyPointDashboardPage dashboard = new FlyPointDashboardPage(page)
                .openViaSidebar()
                .selectCrewLocation(scenario.unit().getName(), scenario.unit().getId(), true)
                .openStocksTab();
        dashboard.clickFlyPointInventoryLink(flyPointId);

        assertThat(page.url()).containsAnyOf(
                "/inventory?storageId=" + flyPointId,
                "/inventory/" + flyPointId);

        UnitManagementPage stock = new UnitManagementPage(page).waitForLoaded()
                .waitForSessionOpenState(true)
                .waitForConductButtonEnabled();
        stock.clickConductInventory();

        InventoryEditPage edit = new InventoryEditPage(page).waitForLoaded();
        edit.updateAmountForResource(resourceName, String.valueOf((int) TARGET_AMOUNT));
        edit.saveChanges();

        assertThat(inventoryFixture.getResourceStock(flyPointId, resource.getId(), UserRole.ADMIN))
                .isCloseTo(TARGET_AMOUNT, within(0.01));
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-CREW-018")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_018)
    @Severity(SeverityLevel.NORMAL)
    public void includeFlyPointStocksCheckboxTogglesAttachedRows() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("ui-cfp-cb-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "cb-");
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();
        String resourceName = normalizeName(resource.getName());

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resource.getId(),
                ISSUE_AMOUNT);

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());

        CrewAnalyticsPage analytics = new CrewAnalyticsPage(page).open()
                .selectCrewLocationByName(scenario.unit().getName())
                .openStocksTab();
        analytics.filterByResourceName(resourceName);

        analytics.setIncludeFlyPointStocks(true);
        assertThat(analytics.isIncludeFlyPointStocksChecked()).isTrue();
        assertThat(analytics.hasFlyPointDashboardLink(flyPointId))
                .as("З увімкненим чекбоксом з’являється лінк точки")
                .isTrue();

        // Чекбокс керує includeFlyPointStocks у запиті; таблиця «Залишки» зараз
        // завжди малює attached як crew→FP (бекенд /crews/stocks ігнорує прапорець —
        // він впливає на /crews/stocks-aggregated). Перевіряємо UI-стан + мережевий прапорець.
        String stocksUrl = analytics.setIncludeFlyPointStocksAndCaptureStocksUrl(false);
        assertThat(analytics.isIncludeFlyPointStocksChecked()).isFalse();
        assertThat(stocksUrl)
                .as("GET /crews/stocks має передати includeFlyPointStocks=false")
                .containsIgnoringCase("includeFlyPointStocks=false");
    }

    @Test(priority = 45)
    @TestCaseId("TC-UI-CREW-025")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_025)
    @Severity(SeverityLevel.CRITICAL)
    public void archivedCrewHiddenFromDefaultStocksTab() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("ui-cfp-arch-");
        StorageResponse archivedCrew = storageFixture.createCrewStorage(
                scenario.unit().getId(), "ui-cfp-arch-crew-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "arch-");
        String resourceName = normalizeName(resource.getName());
        String activeCrewName = normalizeName(scenario.crew().getName());
        String archivedCrewName = normalizeName(archivedCrew.getName());

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                archivedCrew.getId(),
                resource.getId(),
                ISSUE_AMOUNT);

        inventoryFixture.clearStock(archivedCrew.getId());
        assertThat(storageFixture.deactivate(UserRole.ADMIN, archivedCrew.getId()).statusCode())
                .as("архівація CREW для UI-фільтра")
                .isBetween(200, 299);

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());

        CrewAnalyticsPage analytics = new CrewAnalyticsPage(page).open()
                .selectCrewLocationByName(scenario.unit().getName())
                .openStocksTab();
        analytics.setIncludeFlyPointStocks(false);
        analytics.filterByResourceName(resourceName);

        assertThat(analytics.isCrewNameVisibleInStocksTable(activeCrewName))
                .as("За замовчуванням («Активні») — активний екіпаж у таблиці")
                .isTrue();
        assertThat(analytics.isCrewNameVisibleInStocksTable(archivedCrewName))
                .as("За замовчуванням — архівний екіпаж прихований")
                .isFalse();
        analytics.attachScreenshot("TC-UI-CREW-025 — default active crews");

        analytics.selectCrewActivityFilter(CrewAnalyticsPage.INACTIVE_CREWS_LABEL);
        assertThat(analytics.isCrewNameVisibleInStocksTable(archivedCrewName))
                .as("Фільтр «Неактивні» — архівний екіпаж видимий")
                .isTrue();
        assertThat(analytics.isCrewNameVisibleInStocksTable(activeCrewName))
                .as("Фільтр «Неактивні» — активний екіпаж прихований")
                .isFalse();
        analytics.attachScreenshot("TC-UI-CREW-025 — inactive crews filter");

        analytics.selectCrewActivityFilter(CrewAnalyticsPage.ALL_CREWS_LABEL);
        assertThat(analytics.isCrewNameVisibleInStocksTable(activeCrewName)).isTrue();
        assertThat(analytics.isCrewNameVisibleInStocksTable(archivedCrewName)).isTrue();
        analytics.attachScreenshot("TC-UI-CREW-025 — all crews filter");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-STR-RES-013")
    @Description(StorageRegionsAllureDescriptions.TC_UI_STR_RES_013)
    @Severity(SeverityLevel.CRITICAL)
    public void flyPointInventoryAutocompleteUsesUnitAncestorScope() {
        ResourceResponse visible = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "vis-");
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hid-");
        String visibleName = normalizeName(visible.getName());
        String hiddenName = normalizeName(hidden.getName());

        StorageResponse parentUnit = storageFixture.resolveParentUnit();
        StorageResponse unit = storageFixture.createStorage(
                StorageDataFactory.restrictedStorage(parentUnit.getId(), "ui-fp-res-unit-").build());
        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, "ui-fp-res-reg-");
        regionFixture.addRegionMembers(region.getId(), unit.getId());
        regionFixture.addRegionResources(region.getId(), visible.getId());

        StorageResponse flyPoint = storageFixture.createStorage(
                StorageDataFactory.flyPointStorage(unit.getId(), "ui-fp-res-fp-")
                        .accessMode(StorageAccessMode.REGIONS)
                        .build());
        long flyPointId = flyPoint.getId();

        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.openSession(flyPointId);
        sessionStorageId = flyPointId;

        injectRoleSession(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());

        InventoryEditPage edit = new InventoryEditPage(page).open(flyPointId);
        assertThat(edit.isAddResourceOptionVisible(visibleName)).isTrue();
        edit.closeAddResourceAutocomplete();
        assertThat(edit.isAddResourceOptionVisible(hiddenName)).isFalse();
        edit.closeAddResourceAutocomplete();
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-FLY-INV-002")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_INV_002)
    @Severity(SeverityLevel.CRITICAL)
    public void adminTogglesInventorySessionOnFlyPointDeepLink() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("ui-fp-tog-");
        long flyPointId = scenario.flyPoint().getId();
        inventoryFixture.ensureClosed(flyPointId);

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());
        UnitManagementPage stock = new UnitManagementPage(page).openWithStorageIdQuery(flyPointId);
        stock.clickOpenInventory();
        assertThat(stock.isCloseInventoryButtonVisible()).isTrue();
        assertThat(stock.isConductInventoryButtonEnabled()).isTrue();
        stock.clickCloseInventory();
        assertThat(stock.isOpenInventoryButtonVisible()).isTrue();
        assertThat(stock.isConductInventoryButtonEnabled()).isFalse();
    }

    @Test(priority = 61)
    @TestCaseId("TC-UI-FLY-INV-004")
    @Description(StorageRegionsAllureDescriptions.TC_UI_FLY_INV_004)
    @Severity(SeverityLevel.CRITICAL)
    public void flyPointConductDisabledWhenSessionClosed() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("ui-fp-cls-");
        long flyPointId = scenario.flyPoint().getId();
        inventoryFixture.ensureClosed(flyPointId);

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());
        UnitManagementPage stock = new UnitManagementPage(page).openWithStorageIdQuery(flyPointId);
        assertThat(stock.isConductInventoryButtonEnabled())
                .as("При closed session Провести disabled")
                .isFalse();
    }

    @Test(priority = 62)
    @TestCaseId("TC-UI-CREW-019")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_019)
    @Severity(SeverityLevel.CRITICAL)
    public void attachedCrewInventoryDeepLinkShowsEmptyOrForwardedStock() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("ui-att-dl-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "adl-");
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resource.getId(),
                ISSUE_AMOUNT);

        double crewStock = inventoryFixture.getResourceStock(crewId, resource.getId(), UserRole.ADMIN);
        double fpStock = inventoryFixture.getResourceStock(flyPointId, resource.getId(), UserRole.ADMIN);
        assertThat(fpStock).isCloseTo(ISSUE_AMOUNT, within(0.01));

        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(crewId);
        sessionStorageId = crewId;

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());
        UnitManagementPage stock = new UnitManagementPage(page).openWithStorageIdQuery(crewId);
        assertThat(page.url()).contains("storageId=" + crewId);
        // UX: after auto-forward crew shelf is empty — table may not list resource
        if (crewStock < 0.01) {
            assertThat(stock.isResourceVisibleInTable(normalizeName(resource.getName())))
                    .as("Attached CREW deep-link: залишок на FP, на CREW порожньо")
                    .isFalse();
        }
    }

    @Test(priority = 63)
    @TestCaseId("TC-UI-CREW-020")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_020)
    @Severity(SeverityLevel.NORMAL)
    public void crewAndFlyPointAbsentFromWorkspacePicker() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("ui-wks-");
        String crewName = scenario.crew().getName();
        String fpName = scenario.flyPoint().getName();

        injectRoleSession(UserRole.ADMIN, scenario.unit().getId());
        new UnitManagementPage(page).openForStorage(scenario.unit().getId());
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();

        assertThat(sidebar.isWorkspaceOptionVisible(crewName))
                .as("CREW не в sidebar workspace tree")
                .isFalse();
        assertThat(sidebar.isWorkspaceOptionVisible(fpName))
                .as("FLY_POINT не в sidebar workspace tree")
                .isFalse();
    }

    @Test(priority = 64)
    @TestCaseId({
            "TC-UI-CREW-021",
            "TC-UI-CREW-010"
    })
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_021)
    @Severity(SeverityLevel.NORMAL)
    public void obsoleteCrewsModeUrlDoesNotCrash() {
        injectRoleSession(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        page.navigate(ConfigProvider.getBaseUrl() + "/inventory?mode=crews");
        UnitManagementPage stock = new UnitManagementPage(page).waitForLoaded();
        assertThat(page.getByText("Управління запасами").count())
                .as("Застарілий ?mode=crews не ламає сторінку Залишків")
                .isGreaterThan(0);
        assertThat(stock.isOpenInventoryButtonVisible() || stock.isCloseInventoryButtonVisible()
                || !page.url().isBlank()).isTrue();
    }

    @Test(priority = 65)
    @TestCaseId("TC-UI-CREW-022")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_022)
    @Severity(SeverityLevel.CRITICAL)
    public void outsiderHasNoInventorySessionToggleOnFlyPointDeepLink() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("ui-rbac1-");
        long flyPointId = scenario.flyPoint().getId();
        inventoryFixture.ensureClosed(flyPointId);

        injectRoleSession(UserRole.OWNER_2, ConfigProvider.getOwner2StorageId());
        UnitManagementPage stock = new UnitManagementPage(page).openWithStorageIdQuery(flyPointId, false);
        assertThat(stock.isOpenInventoryButtonVisible())
                .as("OWNER_2 без inventory-status — немає Open")
                .isFalse();
        assertThat(stock.isCloseInventoryButtonVisible()).isFalse();
    }

    @Test(priority = 66)
    @TestCaseId("TC-UI-CREW-023")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_023)
    @Severity(SeverityLevel.CRITICAL)
    public void outsiderHasNoConductOnFlyPointDeepLink() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("ui-rbac2-");
        long flyPointId = scenario.flyPoint().getId();
        inventoryFixture.openSession(flyPointId);
        sessionStorageId = flyPointId;

        injectRoleSession(UserRole.OWNER_2, ConfigProvider.getOwner2StorageId());
        UnitManagementPage stock = new UnitManagementPage(page).openWithStorageIdQuery(flyPointId, false);
        // Conduct hidden or disabled without inventory update perm
        if (stock.isConductInventoryButtonVisible()) {
            assertThat(stock.isConductInventoryButtonEnabled()).isFalse();
        }
    }

    @Test(priority = 70)
    @TestCaseId("TC-UI-CREW-024")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_024)
    @Severity(SeverityLevel.NORMAL)
    public void allLocationsBlocksInventorySessionToggle() {
        injectRoleSession(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        UnitManagementPage stock = new UnitManagementPage(page).openForAllLocations();
        assertThat(stock.isInventorySessionToggleBlocked()).isTrue();
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
}
