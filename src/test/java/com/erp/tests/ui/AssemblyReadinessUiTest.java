package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AssemblyReadinessFixture;
import com.erp.fixtures.AssemblyReadinessFixture.TechMapSetup;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.AssemblyReadinessResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.AssemblyReadinessPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.erp.fixtures.AssemblyReadinessFixture.computeReadyQty;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for «Готово до комплектації» ({@code /assembly-readiness}).
 */
@Slf4j
@Epic("Production")
@Feature("Assembly Readiness UI (Готово до комплектації)")
public class AssemblyReadinessUiTest extends BaseUITest {

    private AssemblyReadinessFixture fixture;
    private StorageFixture storageFixture;
    private Long owner1StorageId;
    private Long owner2StorageId;
    private String owner1StorageName;
    private String owner2StorageName;

    private final List<CleanupTechMap> techMapsToCleanup = new ArrayList<>();
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    private record CleanupTechMap(Long techMapId, Long storageId) {
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new AssemblyReadinessFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        owner1StorageName = storageFixture.getNames(UserRole.ADMIN, true, null, owner1StorageId)
                .getFirst().getName();
        owner2StorageName = storageFixture.getNames(UserRole.ADMIN, true, null, owner2StorageId)
                .getFirst().getName();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupUiArtifacts() {
        for (CleanupTechMap entry : techMapsToCleanup) {
            fixture.cleanupTechMap(UserRole.ADMIN, entry.techMapId(), entry.storageId());
        }
        techMapsToCleanup.clear();
        for (Long resourceId : resourcesToCleanup) {
            fixture.cleanupResource(UserRole.ADMIN, resourceId);
        }
        resourcesToCleanup.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-AR-001")
    @Story("Navigation smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1: «Виробництво» → PageTab «Готово до комплектації» → URL /assembly-readiness.")
    public void testNavigationAndHeading() {
        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        AppSidebarPage sidebar = new AppSidebarPage(page);
        assertThat(sidebar.isSidebarVisible()).isTrue();
        assertThat(sidebar.isNavItemVisible(AppSidebarPage.GROUP_PRODUCTION)).isTrue();

        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openViaSidebar();
        assertThat(arPage.isHeadingVisible()).isTrue();
        assertThat(page.url()).contains(AssemblyReadinessPage.PATH);
        arPage.attachScreenshot("TC-UI-AR-001 — assembly readiness page");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-AR-002")
    @Story("All locations guard")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            ADMIN з кількома локаціями + «Всі локації» → warning без API-запиту і без таблиці.
            OWNER_1 з однією локацією не може увійти в all-locations режим (tk-ui StorageContext).""")
    public void testAllLocationsGuard() {
        prepareAuthenticatedPage(UserRole.ADMIN, owner1StorageId);
        injectAllLocationsView();

        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).open();
        assertThat(arPage.isAllLocationsGuardVisible()).isTrue();
        assertThat(arPage.isSortDropdownVisible()).isFalse();
        arPage.attachScreenshot("TC-UI-AR-002 — all locations guard");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-AR-003")
    @Story("Ready qty calculation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI readyQty = min(floor(stock/required)) по компонентах — збігається з API-розрахунком.
            Arrange: in1 req=2 stock=10 → 5; in2 req=1 stock=5 → 5; очікуваний readyQty=5.""")
    public void testReadyQtyMatchesApiCalculation() {
        TechMapSetup setup = arrangeReadyTechMap(2.0, 1.0, 10.0, 5.0);

        AssemblyReadinessResponse apiRow = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();
        int expectedReadyQty = computeReadyQty(apiRow);
        assertThat(expectedReadyQty).isEqualTo(5);

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();

        assertThat(arPage.isProductRowVisible(setup.getTechMap().getName())).isTrue();
        assertThat(arPage.getReadyQtyForRow(setup.getTechMap().getName())).isEqualTo(expectedReadyQty);
        arPage.attachScreenshot("TC-UI-AR-003 — ready qty matches API");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-AR-004")
    @Story("Empty state")
    @Severity(SeverityLevel.NORMAL)
    @Description("Порожня відповідь API → «Немає позицій, готових до комплектації».")
    public void testEmptyState() {
        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        page.route("**/api/v1/assembly-readiness/**", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody("[]")));

        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).open();
        assertThat(arPage.isEmptyStateVisible()).isTrue();
        arPage.attachScreenshot("TC-UI-AR-004 — empty state");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-AR-005")
    @Story("Expand components")
    @Severity(SeverityLevel.NORMAL)
    @Description("Клік по рядку розгортає компоненти; «Вистачить на» збігається з floor(stock/required).")
    public void testExpandRowShowsComponentsWithCorrectPossibleUnits() {
        TechMapSetup setup = arrangeReadyTechMap(2.0, 3.0, 10.0, 7.0);

        AssemblyReadinessResponse apiRow = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();
        int expectedReadyQty = computeReadyQty(apiRow);
        assertThat(expectedReadyQty).isEqualTo(2);

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        arPage.expandRow(setup.getTechMap().getName());

        assertThat(arPage.isComponentsSectionVisible()).isTrue();
        assertThat(arPage.isComponentVisible(setup.getInput1().getName())).isTrue();
        assertThat(arPage.isComponentVisible(setup.getInput2().getName())).isTrue();
        assertThat(arPage.getPossibleUnitsForComponent(setup.getInput1().getName())).isEqualTo(5);
        assertThat(arPage.getPossibleUnitsForComponent(setup.getInput2().getName())).isEqualTo(2);
        assertThat(arPage.getReadyQtyForRow(setup.getTechMap().getName())).isEqualTo(expectedReadyQty);
        arPage.attachScreenshot("TC-UI-AR-005 — expanded components with qty");
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-AR-006")
    @Story("Missing components badge")
    @Severity(SeverityLevel.NORMAL)
    @Description("readyQty=0 → badge «Бракує компонентів».")
    public void testMissingComponentsBadge() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 3.0));

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        assertThat(arPage.isMissingComponentsBadgeVisible(setup.getTechMap().getName())).isTrue();
        assertThat(arPage.getReadyQtyForRow(setup.getTechMap().getName())).isZero();
        arPage.attachScreenshot("TC-UI-AR-006 — missing components badge");
    }

    @Test(priority = 70)
    @TestCaseId("TC-UI-AR-007")
    @Story("Bottleneck badge")
    @Severity(SeverityLevel.NORMAL)
    @Description("Розгорнутий рядок показує «вузьке місце» на лімітуючому компоненті.")
    public void testBottleneckBadge() {
        TechMapSetup setup = arrangeReadyTechMap(2.0, 3.0, 10.0, 7.0);
        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);

        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        arPage.expandRow(setup.getTechMap().getName());
        assertThat(arPage.isBottleneckBadgeVisible()).isTrue();
        arPage.attachScreenshot("TC-UI-AR-007 — bottleneck badge");
    }

    @Test(priority = 80)
    @TestCaseId("TC-UI-AR-008")
    @Story("Shared component badge")
    @Severity(SeverityLevel.NORMAL)
    @Description("Спільний компонент у 2 техкартах → badge «у 2 техкартах».")
    public void testSharedComponentBadge() {
        TechMapSetup first = arrangeReadyTechMap(1.0, 1.0, 8.0, 4.0);
        var in2 = fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0);
        trackResource(in2.getInput2().getId());
        trackResource(in2.getProduct().getId());
        TechnologicalMapResponse second = fixture.createSecondTechMapWithSharedInput(
                UserRole.ADMIN, owner1StorageId,
                first.getInput1(), in2.getInput2(), in2.getProduct());
        trackTechMap(second);
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                in2.getInput2().getId(), 3.0));

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        arPage.expandRow(first.getTechMap().getName());
        assertThat(arPage.isSharedComponentBadgeVisible(2)).isTrue();
        arPage.attachScreenshot("TC-UI-AR-008 — shared component badge");
    }

    @Test(priority = 90)
    @TestCaseId("TC-UI-AR-009")
    @Story("Sorting")
    @Severity(SeverityLevel.NORMAL)
    @Description("Сортування за кількістю ↓ та за назвою А-Я для ізольованих техкарт тесту.")
    public void testSorting() {
        TechMapSetup high = arrangeReadyTechMap(1.0, 1.0, 20.0, 20.0);
        TechMapSetup low = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 5.0, 5.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                low.getInput1().getId(), 5.0,
                low.getInput2().getId(), 5.0));

        String highName = high.getTechMap().getName();
        String lowName = low.getTechMap().getName();

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();

        arPage.selectSortByQuantityDesc();
        int highQty = arPage.getReadyQtyForRow(highName);
        int lowQty = arPage.getReadyQtyForRow(lowName);
        assertThat(highQty).isGreaterThan(lowQty);

        arPage.selectSortByNameAsc();
        List<String> allNames = arPage.collectVisibleTechMapNames();
        int highIdx = allNames.indexOf(highName);
        int lowIdx = allNames.indexOf(lowName);
        assertThat(highIdx).isGreaterThanOrEqualTo(0);
        assertThat(lowIdx).isGreaterThanOrEqualTo(0);

        Collator uk = Collator.getInstance(Locale.forLanguageTag("uk"));
        assertThat(uk.compare(highName, lowName)).isLessThanOrEqualTo(0);
        assertThat(highIdx).isLessThan(lowIdx);
        arPage.attachScreenshot("TC-UI-AR-009 — sorting");
    }

    @Test(priority = 100)
    @TestCaseId("TC-UI-AR-010")
    @Story("Storage switch")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN перемикає локацію → дані перезавантажуються, ізольована техкарта видима лише на своєму storage.")
    public void testStorageSwitchReloadsData() {
        TechMapSetup setup = arrangeReadyTechMap(1.0, 1.0, 5.0, 5.0);
        prepareAuthenticatedPage(UserRole.ADMIN, owner1StorageId);

        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        assertThat(arPage.isProductRowVisible(setup.getTechMap().getName())).isTrue();

        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.waitForResponse(
                r -> r.url().contains("/assembly-readiness/") && "GET".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                () -> new AppSidebarPage(page).selectWorkspaceByName(owner2StorageName));
        arPage.waitForDataSettled();
        assertThat(arPage.isProductRowVisible(setup.getTechMap().getName())).isFalse();

        page.waitForResponse(
                r -> r.url().contains("/assembly-readiness/") && "GET".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                () -> new AppSidebarPage(page).selectWorkspaceByName(owner1StorageName));
        arPage.waitForDataSettled();
        assertThat(arPage.isProductRowVisible(setup.getTechMap().getName())).isTrue();
        arPage.attachScreenshot("TC-UI-AR-010 — storage switch");
    }

    @Test(priority = 110)
    @TestCaseId("TC-UI-AR-011")
    @Story("Component tech map link — happy path")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Компонент з активною PRODUCTION техкартою (ресурс у OUTPUT):
            розгорнути рядок → «Виробляється:» + лінк на техкарту (target=_blank) →
            відкривається /technological-maps/update/{id} у новій вкладці.""")
    public void testComponentTechMapLinkOpensInNewTab() {
        TechMapSetup assembly = arrangeReadyTechMap(1.0, 1.0, 5.0, 5.0);
        TechnologicalMapResponse producer = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        arPage.expandRow(assembly.getTechMap().getName());

        String componentName = assembly.getInput1().getName();
        String producerName = producer.getName();
        assertThat(arPage.isComponentTechMapsLabelVisible(componentName)).isTrue();
        assertThat(arPage.isComponentTechMapLinkVisible(componentName, producerName)).isTrue();
        assertThat(arPage.getComponentTechMapLinkTarget(componentName, producerName)).isEqualTo("_blank");
        assertThat(arPage.getComponentTechMapLinkHref(componentName, producerName))
                .contains("/technological-maps/update/" + producer.getId());

        Page techMapTab = page.waitForPopup(() -> arPage.clickComponentTechMapLink(componentName, producerName));
        techMapTab.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        assertThat(techMapTab.url()).contains("/technological-maps/update/" + producer.getId());
        arPage.attachScreenshot("TC-UI-AR-011 — component tech map link");
        techMapTab.close();
    }

    @Test(priority = 120)
    @TestCaseId("TC-UI-AR-012")
    @Story("Component tech map link — absent when no producer")
    @Severity(SeverityLevel.NORMAL)
    @Description("Компонент без техкарти-виробника → лінк «Виробляється:» не показується.")
    public void testNoTechMapLinkWhenComponentHasNoProducer() {
        TechMapSetup assembly = arrangeReadyTechMap(1.0, 1.0, 5.0, 5.0);

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage arPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        arPage.expandRow(assembly.getTechMap().getName());

        assertThat(arPage.isComponentVisible(assembly.getInput1().getName())).isTrue();
        assertThat(arPage.isComponentTechMapsLabelVisible(assembly.getInput1().getName())).isFalse();
        arPage.attachScreenshot("TC-UI-AR-012 — no tech map link");
    }

    @Test(priority = 130)
    @TestCaseId("TC-UI-AR-013")
    @Story("Component tech map link — admin vs owner storage scope")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Виробник на storage OWNER_2: ADMIN бачить лінк, OWNER_1 — ні
            (UI visibleTechMaps: admin = усі, owner = перетин з allowedStorageIds).""")
    public void testAdminSeesForeignStorageProducerOwnerDoesNot() {
        TechMapSetup assembly = arrangeReadyTechMap(1.0, 1.0, 5.0, 5.0);
        TechnologicalMapResponse foreignProducer = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner2StorageId, assembly.getInput1()),
                owner2StorageId);

        prepareAuthenticatedPage(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessPage ownerPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        ownerPage.expandRow(assembly.getTechMap().getName());
        assertThat(ownerPage.isComponentTechMapLinkVisible(
                assembly.getInput1().getName(), foreignProducer.getName())).isFalse();

        prepareAuthenticatedPage(UserRole.ADMIN, owner1StorageId);
        AssemblyReadinessPage adminPage = new AssemblyReadinessPage(page).openAndWaitForApi();
        adminPage.expandRow(assembly.getTechMap().getName());
        assertThat(adminPage.isComponentTechMapLinkVisible(
                assembly.getInput1().getName(), foreignProducer.getName())).isTrue();
        adminPage.attachScreenshot("TC-UI-AR-013 — admin sees foreign producer");
    }

    private TechMapSetup arrangeReadyTechMap(double in1Req, double in2Req, double in1Stock, double in2Stock) {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, in1Req, in2Req));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), in1Stock,
                setup.getInput2().getId(), in2Stock));
        return setup;
    }

    private TechMapSetup createAndTrack(TechMapSetup setup) {
        trackTechMap(setup.getTechMap(), owner1StorageId);
        trackResource(setup.getProduct().getId());
        trackResource(setup.getInput1().getId());
        trackResource(setup.getInput2().getId());
        return setup;
    }

    private TechnologicalMapResponse trackTechMap(TechnologicalMapResponse techMap) {
        return trackTechMap(techMap, owner1StorageId);
    }

    private TechnologicalMapResponse trackTechMap(TechnologicalMapResponse techMap, Long storageId) {
        techMapsToCleanup.add(new CleanupTechMap(techMap.getId(), storageId));
        return techMap;
    }

    private void trackResource(Long resourceId) {
        resourcesToCleanup.add(resourceId);
    }

    /**
     * Fresh page + cookies + storage init script (init scripts apply only to new navigations).
     */
    private void prepareAuthenticatedPage(UserRole role, long selectedStorageId) {
        browserContext.clearCookies();
        var cookies = getPlaywrightSessionProvider().getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        if (page != null) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
