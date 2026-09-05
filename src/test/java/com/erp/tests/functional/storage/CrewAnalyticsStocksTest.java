package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.CrewResourceCategoryStockResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Аналітика екіпажів — GET /crews/stocks (вкладка «Залишки на екіпажах»).
 * За замовчуванням UI передає {@code active=true}; архівні (деактивовані) CREW не показуються.
 */
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Analytics Stocks")
public class CrewAnalyticsStocksTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-anl-";
    private static final double ISSUE_AMOUNT = 11.0;

    private CrewRegionScenario scenario;
    private StorageResponse archivedCrew;
    private Long resourceId;
    private String resourceName;
    private Long activeCrewId;
    private Long archivedCrewId;
    private Long unitId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: fixtures для crew analytics stocks")
    public void setupCrewAnalyticsStocksTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: активний + архівний екіпаж зі stock")
    public void seedActiveAndArchivedCrewStocks() {
        scenario = crewFixture.prepareSingleCrewScenario("crew-anl-");
        unitId = scenario.unit().getId();
        activeCrewId = scenario.crew().getId();

        archivedCrew = storageFixture.createCrewStorage(unitId, "crew-anl-arch-");
        archivedCrewId = archivedCrew.getId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, scenario.memberStorageId(), activeCrewId, resourceId, ISSUE_AMOUNT);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, scenario.memberStorageId(), archivedCrewId, resourceId, ISSUE_AMOUNT);

        inventoryFixture.clearStock(archivedCrewId);
        assertThat(storageFixture.deactivate(UserRole.ADMIN, archivedCrewId).statusCode())
                .as("архівація CREW після обнулення stock")
                .isBetween(200, 299);

        refreshRoleSessions(UserRole.ADMIN);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-ANL-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_ANL_001)
    @Severity(SeverityLevel.CRITICAL)
    public void activeFilterHidesArchivedCrewFromAnalyticsStocks() {
        List<CrewResourceCategoryStockResponse> activeOnly = crewFixture.getCrewAnalyticsResourceStocks(
                UserRole.ADMIN, unitId, true, resourceName);
        Set<Long> activeCrewIds = crewIds(activeOnly);

        assertThat(activeCrewIds)
                .as("active=true — лише активні екіпажі")
                .contains(activeCrewId)
                .doesNotContain(archivedCrewId);

        List<CrewResourceCategoryStockResponse> inactiveOnly = crewFixture.getCrewAnalyticsResourceStocks(
                UserRole.ADMIN, unitId, false, resourceName);
        Set<Long> inactiveCrewIds = crewIds(inactiveOnly);

        assertThat(inactiveCrewIds)
                .as("active=false — лише архівні екіпажі")
                .contains(archivedCrewId)
                .doesNotContain(activeCrewId);

        List<CrewResourceCategoryStockResponse> allCrews = crewFixture.getCrewAnalyticsResourceStocks(
                UserRole.ADMIN, unitId, null, resourceName);
        Set<Long> allCrewIds = crewIds(allCrews);

        assertThat(allCrewIds)
                .as("active omitted — усі екіпажі зі stock")
                .contains(activeCrewId, archivedCrewId);
    }

    private static Set<Long> crewIds(List<CrewResourceCategoryStockResponse> rows) {
        return rows.stream()
                .map(CrewResourceCategoryStockResponse::getCrewId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
