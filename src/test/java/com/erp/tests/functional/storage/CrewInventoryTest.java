package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.CrewResourceStockResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Залишки та надходження екіпажів через GET /storages/inventory/crews.
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

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: fixtures для crew inventory")
    public void setupCrewInventoryTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: область CREWS + видача (per-method — cleanup деактивує storages)")
    public void seedCrewStockForTest() {
        scenario = crewFixture.prepareSingleCrewScenario("crew-inv-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);
        invalidateCrewManagerSession();
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-INV-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewResourceStockReport() {
        Map<String, Object> params = crewInventoryParams("STOCK");

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_CREW_INVENTORY, UserRole.OWNER_1, params);
        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_GET_CREW_INVENTORY, response);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_GET_CREW_INVENTORY);

        List<CrewResourceStockResponse> rows = crewFixture.getCrewInventory(UserRole.OWNER_1, params);
        CrewResourceStockResponse row = rows.stream()
                .filter(r -> r.getCrew() != null
                        && Objects.equals(r.getCrew().getId(), scenario.crew().getId())
                        && r.getResource() != null
                        && Objects.equals(r.getResource().getId(), resourceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Не знайдено рядок crew stock для crew=" + scenario.crew().getId()
                                + " resource=" + resourceId));

        assertThat(row.getAmount().doubleValue()).isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-INV-007")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_007)
    @Severity(SeverityLevel.CRITICAL)
    public void testOwner1DeniedCrewDirectInventory() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(scenario.crew().getId()));
        assertThat(response.statusCode())
                .as("OWNER_1 без Crew-Manager — direct inventory екіпажу заборонено")
                .isEqualTo(403);
    }

    @Test(priority = 22)
    @TestCaseId("TC-CREW-INV-007b")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_007B)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewManagerCanReadCrewDirectInventory() {
        invalidateCrewManagerSession();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.CREW_MANAGER,
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
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_2,
                String.valueOf(scenario.crew().getId()));
        assertThat(response.statusCode())
                .as("OWNER_2 поза областю CREWS — доступ до inventory екіпажу заборонено")
                .isIn(403, 404);
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-INV-006")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_006)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewStockReportMatchesDirectInventory() {
        invalidateCrewManagerSession();
        double directStock = relocationFixture.getResourceStock(
                scenario.crew().getId(), resourceId, UserRole.CREW_MANAGER);

        Map<String, Object> params = crewInventoryParams("STOCK");
        List<CrewResourceStockResponse> rows = crewFixture.getCrewInventory(UserRole.OWNER_1, params);

        BigDecimal reported = rows.stream()
                .filter(r -> r.getCrew() != null
                        && Objects.equals(r.getCrew().getId(), scenario.crew().getId())
                        && r.getResource() != null
                        && Objects.equals(r.getResource().getId(), resourceId))
                .map(CrewResourceStockResponse::getAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Не знайдено STOCK row для crew/resource"));

        assertThat(reported.doubleValue()).isCloseTo(directStock, within(0.01));
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-INV-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_002)
    @Severity(SeverityLevel.NORMAL)
    public void testCrewResourceIncomeReport() {
        LocalDate today = LocalDate.now();
        Map<String, Object> params = crewInventoryParams("INCOME");
        params.put("fromDate", today.minusDays(1).toString());
        params.put("toDate", today.plusDays(1).toString());

        List<CrewResourceStockResponse> rows = crewFixture.getCrewInventory(UserRole.OWNER_1, params);

        double totalIncome = rows.stream()
                .filter(r -> r.getCrew() != null
                        && Objects.equals(r.getCrew().getId(), scenario.crew().getId())
                        && r.getResource() != null
                        && Objects.equals(r.getResource().getId(), resourceId))
                .map(CrewResourceStockResponse::getIncome)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
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

    @AfterMethod(alwaysRun = true)
    public void closeCrewInventorySession() {
        if (scenario != null && scenario.crew() != null) {
            try {
                inventoryFixture.ensureClosed(scenario.crew().getId());
            } catch (Exception e) {
                log.warn("Crew inventory session cleanup failed: {}", e.getMessage());
            }
        }
    }

    private void invalidateCrewManagerSession() {
        authService.invalidateSession(
                UserRole.CREW_MANAGER.getUsername(),
                UserRole.CREW_MANAGER.getPassword());
    }

    private Map<String, Object> crewInventoryParams(String requestType) {
        Map<String, Object> params = new HashMap<>();
        params.put("storageId", scenario.memberStorageId());
        params.put("requestType", requestType);
        params.put("page", 0);
        params.put("size", 100);
        params.put("groupByUnit", false);
        return params;
    }
}
