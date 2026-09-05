package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.CrewResourceCategoryStockResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.StorageHierarchyResponse;
import com.erp.utils.config.ConfigProvider;
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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Crew-Read-ROLE / Crew-Write-ROLE: перегляд залишків екіпажів свого батальйону
 * ({@code perm_crews-stocks::view} + CREWS region member = {@code unit.storage.id}).
 */
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Battalion Stocks RBAC")
public class CrewBattalionStocksRbacTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-rbac-";
    private static final String SCENARIO_PREFIX = "crew-rbac-";
    private static final double ISSUE_AMOUNT = 9.0;

    private UserFixture userFixture;
    private long battalionMemberStorageId;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private String resourceName;
    private Long unitId;
    private Long crewId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: Crew-Read / Crew-Write users + shared fixtures")
    public void setupCrewBattalionRbacTests() {
        userFixture = new UserFixture(testContext, apiExecutor);
        battalionMemberStorageId = ConfigProvider.getUnitStorageId();
        userFixture.ensureCrewBattalionUser(getPlaywrightSessionProvider(), UserRole.CREW_READ);
        userFixture.ensureCrewBattalionUser(getPlaywrightSessionProvider(), UserRole.CREW_WRITE);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: CREWS region з member=unit.storage.id + stock на екіпажі")
    public void seedBattalionCrewStock() {
        scenario = crewFixture.prepareSingleCrewScenarioForMembers(
                SCENARIO_PREFIX, battalionMemberStorageId);
        unitId = scenario.unit().getId();
        crewId = scenario.crew().getId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, scenario.memberStorageId(), crewId, resourceId, ISSUE_AMOUNT);

        refreshRoleSessions(UserRole.CREW_READ, UserRole.CREW_WRITE);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-RBAC-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_001)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserGetsAccessibleCrewLocations() {
        List<SimpleEntityResponse> locations = crewFixture.getCrewLocations(UserRole.CREW_READ);

        assertThat(locations)
                .extracting(SimpleEntityResponse::getId)
                .contains(unitId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-RBAC-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_002)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserGetsHierarchyForOwnBattalion() {
        List<StorageHierarchyResponse> hierarchy =
                crewFixture.getCrewHierarchy(UserRole.CREW_READ, unitId, true);

        assertThat(hierarchy).isNotEmpty();
        assertThat(flatHierarchyIds(hierarchy)).contains(crewId);
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-RBAC-003")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_003)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserSeesOwnBattalionCrewStocks() {
        List<CrewResourceCategoryStockResponse> stocks = crewFixture.getCrewAnalyticsResourceStocks(
                UserRole.CREW_READ, unitId, true, resourceName);

        Set<Long> crewIds = crewIds(stocks);
        assertThat(crewIds)
                .as("Crew-Read бачить екіпаж свого батальйону зі stock")
                .contains(crewId);

        CrewRegionScenario outsider = crewFixture.prepareSingleCrewScenarioForMembers(
                SCENARIO_PREFIX + "out-", ConfigProvider.getOwner2StorageId());
        refreshRoleSessions(UserRole.CREW_READ);

        Response forbidden = crewFixture.getCrewAnalyticsResourceStocksRaw(
                UserRole.CREW_READ, outsider.unit().getId(), true, resourceName);
        assertThat(forbidden.statusCode())
                .as("Чужий parentId батальйону — 403")
                .isEqualTo(403);
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-RBAC-004")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_004)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadDeniedWhenNotCrewRegionMember() {
        CrewRegionScenario noMember = crewFixture.prepareSingleCrewScenarioForMembers(
                SCENARIO_PREFIX + "nomem-", ConfigProvider.getOwner1StorageId());
        refreshRoleSessions(UserRole.CREW_READ);

        Response response = crewFixture.getCrewAnalyticsResourceStocksRaw(
                UserRole.CREW_READ, noMember.unit().getId(), true, resourceName);
        assertThat(response.statusCode())
                .as("CREWS region без member unit.storage.id — 403 на parentId")
                .isEqualTo(403);
    }

    @Test(priority = 50)
    @TestCaseId("TC-CREW-RBAC-010")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_010)
    @Severity(SeverityLevel.CRITICAL)
    public void crewWriteCanOpenInventorySessionReadCannot() {
        inventoryFixture.ensureClosed(crewId);
        refreshRoleSessions(UserRole.CREW_READ, UserRole.CREW_WRITE);

        Response readOpen = inventoryFixture.putStatus(crewId, UserRole.CREW_READ, true);
        assertThat(readOpen.statusCode())
                .as("Crew-Read не може відкрити inventory session")
                .isEqualTo(403);

        Response writeOpen = inventoryFixture.putStatus(crewId, UserRole.CREW_WRITE, true);
        assertThat(writeOpen.statusCode())
                .as("Crew-Write може відкрити inventory session на CREW у CREWS region")
                .isBetween(200, 299);

        inventoryFixture.ensureClosed(crewId);
    }

    private static Set<Long> crewIds(List<CrewResourceCategoryStockResponse> rows) {
        return rows.stream()
                .map(CrewResourceCategoryStockResponse::getCrewId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Set<Long> flatHierarchyIds(List<StorageHierarchyResponse> nodes) {
        return nodes.stream()
                .flatMap(CrewBattalionStocksRbacTest::walkHierarchy)
                .collect(Collectors.toSet());
    }

    private static java.util.stream.Stream<Long> walkHierarchy(StorageHierarchyResponse node) {
        java.util.stream.Stream<Long> self = java.util.stream.Stream.of(node.getId());
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return self;
        }
        return java.util.stream.Stream.concat(
                self,
                node.getChildren().stream().flatMap(CrewBattalionStocksRbacTest::walkHierarchy));
    }
}
