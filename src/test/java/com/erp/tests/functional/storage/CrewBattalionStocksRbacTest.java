package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.LocationProfileFixture;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Керівник локації» + «Екіпажі: перегляд/облік»: залишки екіпажів свого батальйону
 * з перевіркою CREWS region membership.
 */
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Battalion Stocks RBAC")
public class CrewBattalionStocksRbacTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-rbac-";
    private static final String SCENARIO_PREFIX = "crew-rbac-";
    private static final double ISSUE_AMOUNT = 9.0;

    private UserFixture userFixture;
    private LocationProfileFixture locationProfileFixture;
    private long battalionMemberStorageId;
    private UserFixture.BusinessActor crewStockReader;
    private UserFixture.BusinessActor crewInventoryOperator;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private String resourceName;
    private Long unitId;
    private Long crewId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: тестова локація + свіжі користувачі зі словника бізнес-ролей")
    public void setupCrewBattalionRbacTests() {
        userFixture = new UserFixture(testContext, apiExecutor);
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);
        var battalionLocation = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations()
                .getFirst();
        battalionMemberStorageId = battalionLocation.getId();

        crewStockReader = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.CREW_STOCK_READER,
                List.of(battalionLocation));
        crewInventoryOperator = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.CREW_INVENTORY_OPERATOR,
                List.of(battalionLocation));
        apiExecutor.setSessionForRole(
                UserRole.CREW_READ, crewStockReader.username(), crewStockReader.password());
        apiExecutor.setSessionForRole(
                UserRole.CREW_WRITE, crewInventoryOperator.username(), crewInventoryOperator.password());

        assertThat(userFixture.getMe(UserRole.CREW_READ).getAllowedStorageIds())
                .containsExactlyInAnyOrder(battalionMemberStorageId);
        assertThat(userFixture.getMe(UserRole.CREW_WRITE).getAllowedStorageIds())
                .containsExactlyInAnyOrder(battalionMemberStorageId);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: деактивувати тестових комірників і їхню локацію")
    public void cleanupBusinessActors() {
        if (crewStockReader != null) {
            apiExecutor.evictSessionForRole(UserRole.CREW_READ);
        }
        if (crewInventoryOperator != null) {
            apiExecutor.evictSessionForRole(UserRole.CREW_WRITE);
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
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
    @TestCaseId(
            value = "TC-CREW-RBAC-001",
            roles = BusinessRole.CREW_STOCK_READER,
            locationProfiles = LocationProfile.BATTALION_UNIT)
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_001)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserGetsAccessibleCrewLocations() {
        List<SimpleEntityResponse> locations = crewFixture.getCrewLocations(UserRole.CREW_READ);

        assertThat(locations)
                .extracting(SimpleEntityResponse::getId)
                .contains(unitId);
    }

    @Test(priority = 20)
    @TestCaseId(
            value = "TC-CREW-RBAC-002",
            roles = BusinessRole.CREW_STOCK_READER,
            locationProfiles = LocationProfile.BATTALION_UNIT)
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RBAC_002)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserGetsHierarchyForOwnBattalion() {
        List<StorageHierarchyResponse> hierarchy =
                crewFixture.getCrewHierarchy(UserRole.CREW_READ, unitId, true);

        assertThat(hierarchy).isNotEmpty();
        assertThat(flatHierarchyIds(hierarchy)).contains(crewId);
    }

    @Test(priority = 30)
    @TestCaseId(
            value = "TC-CREW-RBAC-003",
            roles = BusinessRole.CREW_STOCK_READER,
            locationProfiles = LocationProfile.BATTALION_UNIT)
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
    @TestCaseId(
            value = "TC-CREW-RBAC-004",
            roles = BusinessRole.CREW_STOCK_READER,
            locationProfiles = LocationProfile.BATTALION_UNIT)
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
    @TestCaseId(
            value = "TC-CREW-RBAC-010",
            roles = {BusinessRole.CREW_STOCK_READER, BusinessRole.CREW_INVENTORY_OPERATOR},
            locationProfiles = LocationProfile.BATTALION_UNIT)
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
