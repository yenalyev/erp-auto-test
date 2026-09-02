package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.FlyPointResourceStockItemResponse;
import com.erp.models.response.FlyPointStockGroupResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.UnitFlyPointResourceStockResponse;
import com.erp.models.response.UnitShortStatsResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дашборд точок взлету — GET /fly-points/stocks та GET /fly-points/short-stats.
 */
@Epic("Master Data")
@Feature("Storages")
@Story("Fly Point Dashboard")
public class FlyPointDashboardApiTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "fly-dash-";
    private static final double ISSUE_AMOUNT = 12.0;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private String resourceName;
    private Long unitId;
    private Long flyPointId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: fixtures для fly-point dashboard API")
    public void setupFlyPointDashboardApiTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: FLY_POINT зі stock після видачі")
    public void seedFlyPointStock() {
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        scenario = crewFixture.prepareFlyPointScenario("fly-dash-");
        unitId = scenario.unit().getId();
        flyPointId = scenario.flyPoint().getId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, scenario.memberStorageId(), flyPointId, resourceId, ISSUE_AMOUNT);

        refreshRoleSessions(UserRole.ADMIN);
    }

    @Test(priority = 10)
    @TestCaseId("TC-FLY-DASH-001")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_DASH_001)
    @Severity(SeverityLevel.CRITICAL)
    public void flyPointStocksContainsIssuedResource() {
        Response response = crewFixture.getFlyPointStocksRaw(UserRole.ADMIN, unitId, resourceName);
        assertThat(response.statusCode())
                .as("GET /fly-points/stocks")
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.FLY_POINT_GET_STOCKS);

        List<UnitFlyPointResourceStockResponse> units =
                DatabaseIntegrityValidator.extractList(response, UnitFlyPointResourceStockResponse.class);

        List<FlyPointResourceStockItemResponse> items = stockItems(units);
        assertThat(items)
                .as("виданий ресурс на FLY_POINT %s", flyPointId)
                .anySatisfy(item -> {
                    assertThat(item.getResourceId()).isEqualTo(resourceId);
                    assertThat(item.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(ISSUE_AMOUNT));
                });
        assertThat(stockFlyPointIds(units))
                .as("відповідь містить тестову точку взлету")
                .contains(flyPointId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-FLY-DASH-002")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_DASH_002)
    @Severity(SeverityLevel.NORMAL)
    public void flyPointShortStatsReturnsWithinTimeout() {
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        long started = System.currentTimeMillis();
        Response response = crewFixture.getFlyPointShortStatsRaw(UserRole.ADMIN, unitId, 7);
        long elapsed = System.currentTimeMillis() - started;

        assertThat(response.statusCode())
                .as("GET /fly-points/short-stats")
                .isEqualTo(200);
        assertThat(elapsed)
                .as("short-stats має завершитись протягом UI timeout (%d ms)", timeoutMs)
                .isLessThan(timeoutMs);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.FLY_POINT_GET_SHORT_STATS);

        List<UnitShortStatsResponse> stats =
                DatabaseIntegrityValidator.extractList(response, UnitShortStatsResponse.class);
        assertThat(stats).isNotNull();
    }

    private static List<FlyPointResourceStockItemResponse> stockItems(
            List<UnitFlyPointResourceStockResponse> units) {
        return units.stream()
                .flatMap(unit -> stream(unit.getFlyPointStocks()))
                .flatMap(fp -> stream(fp.getResourceCategoryStocks()))
                .flatMap(cat -> stream(cat.getResourceStocks()))
                .filter(item -> item.getResourceId() != null)
                .toList();
    }

    private static List<Long> stockFlyPointIds(List<UnitFlyPointResourceStockResponse> units) {
        return units.stream()
                .flatMap(unit -> stream(unit.getFlyPointStocks()))
                .map(FlyPointStockGroupResponse::getFlyPointId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static <T> Stream<T> stream(List<T> list) {
        return list == null ? Stream.empty() : list.stream();
    }
}
