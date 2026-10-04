package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.UserRole;
import com.erp.enums.BusinessRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.UserFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.CrewResourceCategoryStockResponse;
import com.erp.models.response.CrewResourceStockItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.PollUtils;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Залишки екіпажів: GET /crews/stocks та direct GET /storages/{crewId}/inventory.
 * Owner читає через crews analytics; direct GET без {@code inventory-list::{crew}::read} → 403 (AC-04).
 * Crew-Manager має direct read (AC-05); OWNER поза CREWS / без membership → 403/404 (AC-06).
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Inventory")
public class CrewInventoryTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-inv-";
    private static final double ISSUE_AMOUNT = 20.0;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private UserFixture dynamicUserFixture;
    private boolean classSetupReady;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: fixtures для crew inventory")
    public void setupCrewInventoryTests() {
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        classSetupReady = true;
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: область CREWS + видача (per-method — cleanup деактивує storages)")
    public void seedCrewStockForTest() {
        if (!classSetupReady) {
            throw new SkipException("Crew inventory class fixture setup did not complete");
        }
        StorageResponse stockSource = storageFixture.createChildStorage(
                owner1StorageId, "crew-inv-source-");
        scenario = crewFixture.prepareSingleCrewScenario("crew-inv-", stockSource.getId());
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.ADMIN,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.CREW_MANAGER);
        waitForCrewStock(ISSUE_AMOUNT);
    }

    private void waitForCrewStock(double expectedAmount) {
        PollUtils.waitUntilTrue(
                () -> Math.abs(relocationFixture.getResourceStock(
                        scenario.crew().getId(), resourceId, UserRole.ADMIN) - expectedAmount) < 0.01,
                15_000,
                "Crew stock for crew=" + scenario.crew().getId() + " resource=" + resourceId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-INV-001")
    @Description("GET /crews/stocks returns the dynamically issued resource under the crew.")
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewResourceStockReport() {
        configureDynamicCrewReader();
        Map<String, Object> params = crewStockParams();

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.CREW_GET_RESOURCE_STOCKS, UserRole.CREW_READ, params);
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(reportedCrewStock()).isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-INV-007")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_007)
    @Severity(SeverityLevel.CRITICAL)
    public void owner1DeniedDirectCrewInventoryWithoutInventoryListPerm() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                uiInventoryParams(),
                String.valueOf(scenario.crew().getId()));
        assertThat(response.statusCode())
                .as("OWNER_1 без inventory-list::{crew}::read — direct GET inventory екіпажу заборонено (AC-04)")
                .isEqualTo(403);
    }

    @Test(priority = 22)
    @TestCaseId("TC-CREW-INV-007b")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_007B)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewManagerCanReadCrewDirectInventory() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.CREW_MANAGER,
                uiInventoryParams(),
                String.valueOf(scenario.crew().getId()));
        assertThat(response.statusCode())
                .as("Crew-Manager (argument) має inventory-list::{crew}::read")
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_GET);

        double stock = relocationFixture.getResourceStock(
                scenario.crew().getId(), resourceId, UserRole.CREW_MANAGER);
        assertThat(stock).isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 25)
    @TestCaseId("TC-CREW-INV-008")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_008)
    @Severity(SeverityLevel.NORMAL)
    public void testOwner2DeniedCrewDirectInventory() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_2,
                uiInventoryParams(),
                String.valueOf(scenario.crew().getId()));
        assertThat(response.statusCode())
                .as("OWNER_2 поза областю CREWS — доступ до inventory екіпажу заборонено")
                .isIn(403, 404);
    }

    @Test(priority = 26)
    @TestCaseId("TC-CREW-INV-008b")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_008B)
    @Severity(SeverityLevel.CRITICAL)
    public void testOwner1DeniedUnattachedCrewInventory() {
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, scenario.memberStorageId());
        Long parentId = member.getParent() != null ? member.getParent().getId() : member.getId();
        StorageResponse unit = storageFixture.createUnitStorage(parentId, "crew-inv-out-unit-");
        StorageResponse unattachedCrew = storageFixture.createCrewStorage(unit.getId(), "crew-inv-out-crew-");

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                uiInventoryParams(),
                String.valueOf(unattachedCrew.getId()));
        assertThat(response.statusCode())
                .as("OWNER_1 — inventory екіпажу поза CREWS локації заборонено")
                .isEqualTo(403);
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-INV-006")
    @Description("GET /crews/stocks agrees with direct inventory on the same dynamic crew/resource.")
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewStockReportMatchesDirectInventory() {
        configureDynamicCrewReader();
        double directStock = relocationFixture.getResourceStock(
                scenario.crew().getId(), resourceId, UserRole.CREW_MANAGER);

        assertThat(reportedCrewStock()).isCloseTo(directStock, within(0.01));
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-INV-002")
    @Description("GET /crews/relocations reports the dynamically issued resource as crew income.")
    @Severity(SeverityLevel.NORMAL)
    public void testCrewResourceIncomeReport() {
        configureDynamicCrewReader();
        LocalDate today = LocalDate.now();
        Map<String, Object> params = new HashMap<>();
        params.put("parentId", scenario.unit().getId());
        params.put("resourceName", resourceName());
        params.put("page", 0);
        params.put("size", 100);
        params.put("fromDate", today.minusDays(1).toString());
        params.put("toDate", today.plusDays(1).toString());

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.CREW_GET_RELOCATIONS, UserRole.CREW_READ, params);
        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> rows = response.jsonPath().getList("content");
        double totalIncome = rows.stream()
                .filter(r -> Objects.equals(((Number) r.get("crewId")).longValue(), scenario.crew().getId())
                        && Objects.equals(((Number) r.get("resourceId")).longValue(), resourceId))
                .map(r -> (Number) r.get("ingressAmount"))
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();

        assertThat(totalIncome).isGreaterThanOrEqualTo(ISSUE_AMOUNT);
    }

    @Test(priority = 45)
    @TestCaseId("TC-CREW-INV-009")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_009)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewInventorySessionOpenRbac() {
        inventoryFixture.ensureClosed(scenario.crew().getId());

        Response adminOpen = inventoryFixture.putStatus(
                scenario.crew().getId(), UserRole.ADMIN, true);
        assertThat(adminOpen.statusCode())
                .as("ADMIN може відкрити сесію інвентаризації на crew storage")
                .isBetween(200, 299);
        inventoryFixture.ensureClosed(scenario.crew().getId());

        Response owner2Open = inventoryFixture.putStatus(
                scenario.crew().getId(), UserRole.OWNER_2, true);
        assertThat(owner2Open.statusCode())
                .as("OWNER_2 поза CREWS — відкриття сесії заборонено")
                .isIn(403, 404);
    }

    @Test(priority = 50)
    @TestCaseId("TC-CREW-INV-010")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_010)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewInventoryConductUpdatesStock() {
        long crewId = scenario.crew().getId();
        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(crewId);

        double targetAmount = ISSUE_AMOUNT + 5.0;
        try {
            inventoryFixture.setResourceAmount(crewId, UserRole.ADMIN, resourceId, targetAmount);
            double stock = relocationFixture.getResourceStock(crewId, resourceId, UserRole.ADMIN);
            assertThat(stock).isCloseTo(targetAmount, within(0.01));
        } finally {
            inventoryFixture.closeSession(crewId);
        }
    }

    @Test(priority = 55)
    @TestCaseId("TC-CREW-INV-014")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_014)
    @Severity(SeverityLevel.CRITICAL)
    public void putInventoryOnClosedCrewSessionReturns403() {
        long crewId = scenario.crew().getId();
        inventoryFixture.ensureClosed(crewId);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                inventoryFixture.listItems(crewId, UserRole.ADMIN),
                Map.of(resourceId, ISSUE_AMOUNT + 1.0));
        Response response = inventoryFixture.conductInventoryRaw(crewId, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(relocationFixture.getResourceStock(crewId, resourceId, UserRole.ADMIN))
                .isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 56)
    @TestCaseId("TC-CREW-INV-015")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_015)
    @Severity(SeverityLevel.CRITICAL)
    public void crewManagerCanOpenAndConductInventory() {
        long crewId = scenario.crew().getId();
        inventoryFixture.ensureClosed(crewId);
        refreshRoleSessions(UserRole.CREW_MANAGER);

        Response open = inventoryFixture.putStatus(crewId, UserRole.CREW_MANAGER, true);
        assertThat(open.statusCode())
                .as("Crew-Manager може відкрити сесію на CREW у CREWS region")
                .isBetween(200, 299);
        try {
            double target = ISSUE_AMOUNT + 2.0;
            InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                    inventoryFixture.listItems(crewId, UserRole.CREW_MANAGER),
                    Map.of(resourceId, target));
            Response put = inventoryFixture.conductInventoryRaw(crewId, UserRole.CREW_MANAGER, request);
            assertThat(put.statusCode())
                    .as("Crew-Manager PUT inventory")
                    .isBetween(200, 299);
            assertThat(relocationFixture.getResourceStock(crewId, resourceId, UserRole.CREW_MANAGER))
                    .isCloseTo(target, within(0.01));
        } finally {
            inventoryFixture.ensureClosed(crewId);
        }
    }

    @Test(priority = 57)
    @TestCaseId("TC-CREW-INV-NEG-01")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_NEG_01)
    @Severity(SeverityLevel.CRITICAL)
    public void putNegativeAmountOnCrewReturns400() {
        long crewId = scenario.crew().getId();
        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(crewId);
        try {
            InventoryRequest bad = InventoryDataFactory.mergeWithExisting(
                    inventoryFixture.listItems(crewId, UserRole.ADMIN),
                    Map.of(resourceId, -2.0));
            Response response = inventoryFixture.conductInventoryRaw(crewId, UserRole.ADMIN, bad);
            assertThat(response.statusCode()).isEqualTo(400);
        } finally {
            inventoryFixture.closeSession(crewId);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void closeCrewInventorySession() {
        if (scenario != null && scenario.crew() != null) {
            try {
                inventoryFixture.ensureClosed(scenario.crew().getId());
            } catch (Exception e) {
                log.warn("Crew inventory session cleanup failed: {}", e.getMessage());
            }
        }
        if (dynamicUserFixture != null) {
            apiExecutor.evictSessionForRole(UserRole.CREW_READ);
            dynamicUserFixture.deactivateTrackedUsers();
            dynamicUserFixture = null;
        }
        if (scenario != null && resourceId != null && inventoryFixture != null) {
            for (Long storageId : List.of(scenario.crew().getId(), scenario.memberStorageId())) {
                try {
                    inventoryFixture.removeResourceFromStorage(storageId, resourceId, UserRole.ADMIN);
                } catch (RuntimeException cleanupError) {
                    log.warn("Crew inventory stock cleanup failed for resource {} on storage {}",
                            resourceId, storageId, cleanupError);
                }
            }
        }
        if (regionFixture != null && storageFixture != null) {
            TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
        }
        if (resourceFixture != null && resourceId != null) {
            try {
                Response response = resourceFixture.deactivate(UserRole.ADMIN, resourceId);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Crew inventory resource {} cleanup returned HTTP {}: {}",
                            resourceId, response.statusCode(), response.asString());
                }
            } catch (RuntimeException cleanupError) {
                log.warn("Crew inventory resource {} cleanup failed", resourceId, cleanupError);
            } finally {
                resourceId = null;
            }
        }
    }

    /** Query params as UI /unit-management?mode=crews&crew=… */
    private Map<String, Object> uiInventoryParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchTerm", "");
        params.put("page", 0);
        params.put("size", 100);
        params.put("sort", List.of("weight,desc", "resource.name,asc"));
        return params;
    }

    private Map<String, Object> crewStockParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("parentId", scenario.unit().getId());
        params.put("active", true);
        params.put("resourceName", resourceName());
        return params;
    }

    private String resourceName() {
        return resourceFixture.getById(UserRole.ADMIN, resourceId).getName();
    }

    private double reportedCrewStock() {
        return crewFixture.getCrewAnalyticsResourceStocks(
                        UserRole.CREW_READ, scenario.unit().getId(), true, resourceName()).stream()
                .filter(row -> Objects.equals(row.getCrewId(), scenario.crew().getId()))
                .map(CrewResourceCategoryStockResponse::getResourceStocks)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(item -> Objects.equals(item.getResourceId(), resourceId))
                .map(CrewResourceStockItemResponse::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No crew stock for crew=" + scenario.crew().getId()
                        + " resource=" + resourceId));
    }

    private void configureDynamicCrewReader() {
        dynamicUserFixture = new UserFixture(testContext, apiExecutor);
        UserFixture.BusinessActor actor = dynamicUserFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.CREW_STOCK_READER, List.of(scenario.unit()));
        apiExecutor.setSessionForRole(UserRole.CREW_READ, actor.username(), actor.password());
    }
}
