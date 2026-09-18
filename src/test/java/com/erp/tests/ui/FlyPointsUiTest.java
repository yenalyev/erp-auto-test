package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.FlyPointDetailPage;
import com.erp.pages.FlyPointsPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** UI-покриття нового кабінету «Точки вильоту» для комірника батальйону. */
@Epic("Inventory")
@Feature("Fly points cabinet")
@Story("Battalion keeper fly points")
public class FlyPointsUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern POINTS_ROUTE = Pattern.compile(".*/api/v1/fly-points(?:\\?.*)?$");
    private static final Pattern UNASSIGNED_CREWS_ROUTE =
            Pattern.compile(".*/api/v1/fly-points/unassigned-crews(?:\\?.*)?$");

    private LocationProfileFixture locationProfiles;
    private StorageFixture storages;
    private UserFixture users;
    private StorageResponse warehouse;
    private StorageResponse pointAlpha;
    private StorageResponse pointZulu;
    private StorageResponse attachedCrew;
    private StorageResponse unassignedCrew;
    private UserFixture.BusinessActor keeper;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        LocationProfileFixture.LocationSet battalion =
                locationProfiles.create(LocationProfile.BATTALION_WARENHAUSE_UNIT, 1);
        warehouse = battalion.locations().getFirst();
        pointAlpha = storages.createFlyPointStorage(warehouse.getId(), "fp-page-alpha-");
        pointZulu = storages.createFlyPointStorage(warehouse.getId(), "fp-page-zulu-");
        attachedCrew = storages.createCrewStorage(pointAlpha.getId(), "fp-page-attached-");
        unassignedCrew = storages.createCrewStorage(warehouse.getId(), "fp-page-unassigned-");
        keeper = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.UNIT_KOMIRNIK, List.of(warehouse));

        injectSessionCookies(
                authService.getSessionForUser(keeper.username(), keeper.password()),
                sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + warehouse.getId() + "');");
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFlyPointsScenario() {
        if (users != null) {
            users.deactivateTrackedUsers();
        }
        if (storages != null) {
            storages.deactivateTrackedStorages(UserRole.ADMIN);
        }
        if (locationProfiles != null) {
            locationProfiles.cleanup();
        }
    }

    @Test(priority = 10)
    @TestCaseId(value = "TC-UI-FLY-POINTS-001", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Комірник батальйону відкриває «Точки вильоту» із сайдбару; бачить заголовок "
            + "вибраного батальйону, дефолтну вкладку та блок фільтрів/сортування.")
    public void battalionKeeperOpensFlyPointsFromSidebar() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).openViaSidebar();
        flyPoints.waitForPointCards(2);

        assertThat(flyPoints.isSidebarEntryVisible()).isTrue();
        assertThat(flyPoints.isSidebarEntryActive()).isTrue();
        assertThat(page.url()).contains("/fly-points");
        assertThat(flyPoints.heading()).isEqualTo("Точки вильоту " + warehouse.getName());
        assertThat(flyPoints.isTabVisible(FlyPointsPage.POINTS_TAB)).isTrue();
        assertThat(flyPoints.isTabVisible(FlyPointsPage.UNASSIGNED_CREWS_TAB)).isTrue();
        assertThat(flyPoints.isTabSelected(FlyPointsPage.POINTS_TAB)).isTrue();
        assertThat(flyPoints.areFiltersVisible()).isTrue();
        assertThat(flyPoints.selectedStatus()).isEqualTo(FlyPointsPage.ACTIVE_STATUS);
        assertThat(flyPoints.selectedSort()).isEqualTo(FlyPointsPage.NAME_ASC);
        flyPoints.attachScreenshot("TC-UI-FLY-POINTS-001 — сторінка точок вильоту");
    }

    @Test(priority = 20)
    @TestCaseId(value = "TC-UI-FLY-POINTS-002", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Картка точки показує назву, кількість екіпажів і наявність проблем зі списанням; "
            + "картка без екіпажів має відповідний статус.")
    public void flyPointCardsShowCrewAndWriteOffState() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).open();
        flyPoints.waitForPointCards(2);

        assertThat(flyPoints.pointCardText(pointAlpha.getId()))
                .contains(pointAlpha.getName(), "Екіпажів: 1");
        assertThat(flyPoints.writeOffIssueTitle(pointAlpha.getId()))
                .isEqualTo("Проблеми зі списанням: 2");
        assertThat(flyPoints.pointCardText(pointZulu.getId()))
                .contains(pointZulu.getName(), "Без екіпажів");
        assertThat(flyPoints.writeOffIssueTitle(pointZulu.getId())).isNull();
    }

    @Test(priority = 30)
    @TestCaseId(value = "TC-UI-FLY-POINTS-003", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.NORMAL)
    @Description("Пошук, статус і сортування змінюють набір та порядок карток точок вильоту.")
    public void filtersAndSortingChangeVisibleFlyPointCards() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).open();
        flyPoints.waitForPointCards(2);
        assertThat(flyPoints.visiblePointNames())
                .containsExactly(pointAlpha.getName(), pointZulu.getName());

        flyPoints.selectSort(FlyPointsPage.NAME_DESC);
        assertThat(flyPoints.visiblePointNames())
                .containsExactly(pointZulu.getName(), pointAlpha.getName());

        flyPoints.selectStatus(FlyPointsPage.ALL_STATUSES);
        flyPoints.waitForPointCards(3);
        assertThat(flyPoints.visiblePointNames()).contains(inactivePointName());

        flyPoints.search(pointAlpha.getName());
        flyPoints.waitForPointCards(1);
        assertThat(flyPoints.visiblePointNames()).containsExactly(pointAlpha.getName());
    }

    @Test(priority = 40)
    @TestCaseId(value = "TC-UI-FLY-POINTS-004", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.NORMAL)
    @Description("Вкладка «Екіпажі без точок» відкривається за view=crews і показує картки "
            + "неприв'язаних екіпажів.")
    public void unassignedCrewsTabShowsCrewCards() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).open()
                .openUnassignedCrews();
        flyPoints.waitForCrewCards(1);

        assertThat(flyPoints.isTabSelected(FlyPointsPage.UNASSIGNED_CREWS_TAB)).isTrue();
        assertThat(page.url()).contains("view=crews");
        assertThat(flyPoints.hasCrewCard(unassignedCrew.getId())).isTrue();
        assertThat(flyPoints.crewCardText(unassignedCrew.getId()))
                .contains(unassignedCrew.getName(), "Без точки вильоту", "Без залишків");
    }

    @Test(priority = 50)
    @TestCaseId(value = "TC-UI-FLY-POINTS-005", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Клік по картці відкриває сторінку точки з заголовком і вкладками; "
            + "«Залишки» є дефолтною вкладкою.")
    public void pointCardOpensDetailWithStocksAsDefaultTab() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).open();
        flyPoints.waitForPointCards(2);
        FlyPointDetailPage detail = flyPoints.openPoint(pointAlpha.getId())
                .waitForHeading(pointAlpha.getName());

        assertThat(detail.heading()).isEqualTo("Точка вильоту " + pointAlpha.getName());
        assertThat(detail.isTabVisible(FlyPointDetailPage.STOCKS_TAB)).isTrue();
        assertThat(detail.isTabVisible(FlyPointDetailPage.INCOMING_TAB)).isTrue();
        assertThat(detail.isTabVisible(FlyPointDetailPage.USAGE_TAB)).isTrue();
        assertThat(detail.isTabSelected(FlyPointDetailPage.STOCKS_TAB))
                .as("«Залишки» мають бути дефолтною вкладкою сторінки точки")
                .isTrue();
    }

    @Test(priority = 60)
    @TestCaseId(value = "TC-UI-FLY-POINTS-006", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Вкладка «Залишки» відкриває вже реалізовану сторінку inventory з storageId точки.")
    public void stocksTabOpensExistingFlyPointInventory() {
        mockFlyPoints();

        FlyPointsPage flyPoints = new FlyPointsPage(page).open();
        flyPoints.waitForPointCards(2);
        FlyPointDetailPage detail = flyPoints.openPoint(pointAlpha.getId());
        detail.openStocks(pointAlpha.getId());

        assertThat(page.url()).contains("/inventory?storageId=" + pointAlpha.getId());
    }

    private void mockFlyPoints() {
        page.route(POINTS_ROUTE, route -> {
            String term = query(route.request().url(), "term");
            List<Map<String, Object>> points = pointCards();
            if (term != null && !term.isBlank()) {
                String normalized = term.toLowerCase(Locale.ROOT);
                points = points.stream()
                        .filter(point -> String.valueOf(point.get("name"))
                                .toLowerCase(Locale.ROOT)
                                .contains(normalized))
                        .toList();
            }
            fulfillJson(route, points);
        });
        page.route(UNASSIGNED_CREWS_ROUTE, route -> fulfillJson(route, List.of(Map.of(
                "id", unassignedCrew.getId(),
                "name", unassignedCrew.getName(),
                "active", true,
                "parent", parent(),
                "hasStock", false))));
    }

    private List<Map<String, Object>> pointCards() {
        return List.of(
                point(pointAlpha.getId(), pointAlpha.getName(), true, true, 1, 2),
                point(pointZulu.getId(), pointZulu.getName(), true, true, 0, 0),
                point(9_000_000_003L, inactivePointName(), false, false, 0, 0));
    }

    private Map<String, Object> point(long id, String name, boolean active, boolean hasStock,
                                      int crewsCount, int writeOffIssuesCount) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("name", name);
        value.put("active", active);
        value.put("parent", parent());
        value.put("hasStock", hasStock);
        value.put("crewsCount", crewsCount);
        value.put("writeOffIssuesCount", writeOffIssuesCount);
        return value;
    }

    private Map<String, Object> parent() {
        return Map.of("id", warehouse.getId(), "name", warehouse.getName());
    }

    private String inactivePointName() {
        return "Middle inactive point " + warehouse.getId();
    }

    private static String query(String url, String key) {
        String raw = URI.create(url).getRawQuery();
        if (raw == null) {
            return null;
        }
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).replace("[]", "");
            if (name.equals(key) && pair.length == 2) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void fulfillJson(Route route, Object body) {
        try {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(JSON.writeValueAsString(body)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize mocked fly-points response", e);
        }
    }
}
