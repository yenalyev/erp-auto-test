package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.InventorySessionStatus;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Інвентаризація на FLY_POINT (точка вильоту): сесія, conduct, RBAC.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Fly Point Inventory")
public class FlyPointInventoryTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "fp-inv-";
    private static final double ISSUE_AMOUNT = 15.0;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private Long flyPointId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка fixtures для fly-point inventory")
    public void setupFlyPointInventoryTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Підготовка: FLY_POINT + stock")
    public void seedFlyPointStock() {
        scenario = crewFixture.prepareFlyPointScenario("fp-inv-");
        flyPointId = scenario.flyPoint().getId();
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                flyPointId,
                resourceId,
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.OWNER_2);
        inventoryFixture.ensureClosed(flyPointId);
    }

    @AfterMethod(alwaysRun = true)
    public void closeFlyPointInventorySession() {
        if (flyPointId != null) {
            try {
                inventoryFixture.ensureClosed(flyPointId);
            } catch (Exception e) {
                log.warn("Fly-point inventory session cleanup failed: {}", e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-FLY-INV-001")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_001)
    @Severity(SeverityLevel.CRITICAL)
    public void adminOpensAndClosesInventorySessionOnFlyPoint() {
        InventorySessionStatus opened = inventoryFixture.openSession(flyPointId);
        assertThat(opened.getOpen()).isTrue();
        assertThat(inventoryFixture.getStatus(flyPointId, UserRole.ADMIN).getOpen()).isTrue();

        InventorySessionStatus closed = inventoryFixture.closeSession(flyPointId);
        assertThat(closed.getOpen()).isFalse();
        assertThat(inventoryFixture.getStatus(flyPointId, UserRole.ADMIN).getOpen()).isFalse();
    }

    @Test(priority = 20)
    @TestCaseId("TC-FLY-INV-002")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_002)
    @Severity(SeverityLevel.CRITICAL)
    public void putInventoryUpdatesFlyPointStock() {
        inventoryFixture.openSession(flyPointId);
        double target = ISSUE_AMOUNT + 5.0;
        try {
            inventoryFixture.setResourceAmount(flyPointId, UserRole.ADMIN, resourceId, target);
            assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                    .isCloseTo(target, within(0.01));
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-FLY-INV-003")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_003)
    @Severity(SeverityLevel.CRITICAL)
    public void putInventoryOnClosedFlyPointSessionReturns403() {
        inventoryFixture.ensureClosed(flyPointId);
        List<StorageItemResponse> items = inventoryFixture.listItems(flyPointId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(resourceId, ISSUE_AMOUNT + 1.0));

        Response response = inventoryFixture.conductInventoryRaw(flyPointId, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                .isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 40)
    @TestCaseId("TC-FLY-INV-004")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_004)
    @Severity(SeverityLevel.CRITICAL)
    public void outsiderCannotOpenOrConductInventoryOnFlyPoint() {
        Response owner2Open = inventoryFixture.putStatus(flyPointId, UserRole.OWNER_2, true);
        assertThat(owner2Open.statusCode()).isIn(403, 404);

        inventoryFixture.openSession(flyPointId);
        try {
            List<StorageItemResponse> items = inventoryFixture.listItems(flyPointId, UserRole.ADMIN);
            InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                    items, Map.of(resourceId, ISSUE_AMOUNT + 2.0));
            Response conduct = inventoryFixture.conductInventoryRaw(
                    flyPointId, UserRole.OWNER_2, request);
            assertThat(conduct.statusCode()).isIn(403, 404);
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 50)
    @TestCaseId("TC-FLY-INV-005")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_005)
    @Severity(SeverityLevel.NORMAL)
    public void addAndRemoveResourceOnFlyPointInventory() {
        ResourceResponse extra = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "extra-");
        long extraId = extra.getId();

        inventoryFixture.openSession(flyPointId);
        try {
            InventoryRequest addRequest = InventoryDataFactory.mergeWithExisting(
                    inventoryFixture.listItems(flyPointId, UserRole.ADMIN),
                    Map.of(resourceId, ISSUE_AMOUNT, extraId, 4.0));
            inventoryFixture.conductInventory(flyPointId, UserRole.ADMIN, addRequest);
            assertThat(relocationFixture.getResourceStock(flyPointId, extraId, UserRole.ADMIN))
                    .isCloseTo(4.0, within(0.01));

            InventoryRequest removeRequest = InventoryDataFactory.copyExcept(
                    inventoryFixture.listItems(flyPointId, UserRole.ADMIN), extraId);
            inventoryFixture.conductInventory(flyPointId, UserRole.ADMIN, removeRequest);
            assertThat(relocationFixture.getResourceStock(flyPointId, extraId, UserRole.ADMIN))
                    .isEqualTo(0.0);
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 55)
    @TestCaseId("TC-FLY-INV-006")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_006)
    @Severity(SeverityLevel.NORMAL)
    public void putInventoryOnFlyPointRecordedInOperationHistory() {
        inventoryFixture.openSession(flyPointId);
        try {
            inventoryFixture.setResourceAmount(flyPointId, UserRole.ADMIN, resourceId, ISSUE_AMOUNT + 3.0);
            Response history = inventoryFixture.getOperationHistoryToday(flyPointId, UserRole.ADMIN);
            if (history.statusCode() == 403) {
                throw new org.testng.SkipException("Немає права resource-operation-history");
            }
            assertThat(history.statusCode()).isEqualTo(200);
            assertThat(history.getBody().asString()).containsAnyOf("ADDED_INV", "REMOVED_INV");
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 60)
    @TestCaseId("TC-FLY-INV-008")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_008)
    @Severity(SeverityLevel.CRITICAL)
    public void multiLocationInventoryIncludesFlyPoint() {
        String locations = scenario.memberStorageId() + "," + flyPointId;
        Response response = inventoryFixture.getMultiLocationInventory(UserRole.ADMIN, locations);
        assertThat(response.statusCode()).isEqualTo(200);

        boolean found = response.jsonPath().getList("content").stream()
                .anyMatch(row -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) row;
                    Object resource = map.get("resource");
                    if (!(resource instanceof Map<?, ?> resMap)) {
                        return false;
                    }
                    Object id = resMap.get("id");
                    if (id == null || !resourceId.equals(((Number) id).longValue())) {
                        return false;
                    }
                    Object locs = map.get("locations");
                    if (!(locs instanceof List<?> locList)) {
                        return false;
                    }
                    return locList.stream().anyMatch(loc -> {
                        if (!(loc instanceof Map<?, ?> locMap)) {
                            return false;
                        }
                        Object storage = locMap.get("storage");
                        if (!(storage instanceof Map<?, ?> stMap)) {
                            return false;
                        }
                        Object sid = stMap.get("id");
                        return sid != null && flyPointId.equals(((Number) sid).longValue());
                    });
                });
        assertThat(found)
                .as("Multi-location inventory має містити resource на FLY_POINT")
                .isTrue();
    }

    @Test(priority = 70)
    @TestCaseId("TC-FLY-INV-010")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_010)
    @Severity(SeverityLevel.MINOR)
    public void externalFlyPointInventoryPolicy() {
        StorageResponse externalFp = storageFixture.createStorage(
                StorageDataFactory.flyPointStorage(scenario.unit().getId(), "fp-ext-")
                        .relation(com.erp.enums.StorageRelation.EXTERNAL)
                        .build());
        long extId = externalFp.getId();
        inventoryFixture.ensureClosed(extId);
        Response open = inventoryFixture.putStatus(extId, UserRole.ADMIN, true);
        if (open.statusCode() >= 400) {
            assertThat(open.statusCode())
                    .as("EXTERNAL FLY_POINT: open session заборонено політикою")
                    .isBetween(400, 499);
            return;
        }
        try {
            InventoryRequest request = InventoryDataFactory.seedAmounts(Map.of(resourceId, 5.0));
            Response put = inventoryFixture.conductInventoryRaw(extId, UserRole.ADMIN, request);
            assertThat(put.statusCode())
                    .as("EXTERNAL FLY_POINT inventory: 2xx (як INV-REL) або 4xx за політикою")
                    .isIn(200, 201, 400, 403, 422);
        } finally {
            inventoryFixture.ensureClosed(extId);
        }
    }

    @Test(priority = 80)
    @TestCaseId("TC-FLY-INV-NEG-01")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_NEG_01)
    @Severity(SeverityLevel.CRITICAL)
    public void putNegativeAmountOnFlyPointReturns400() {
        inventoryFixture.openSession(flyPointId);
        try {
            InventoryRequest bad = InventoryDataFactory.mergeWithExisting(
                    inventoryFixture.listItems(flyPointId, UserRole.ADMIN),
                    Map.of(resourceId, -1.0));
            Response response = inventoryFixture.conductInventoryRaw(flyPointId, UserRole.ADMIN, bad);
            assertThat(response.statusCode()).isEqualTo(400);
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 81)
    @TestCaseId("TC-FLY-INV-NEG-02")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_NEG_02)
    @Severity(SeverityLevel.NORMAL)
    public void putUnknownResourceOnFlyPointReturns4xx() {
        inventoryFixture.openSession(flyPointId);
        try {
            InventoryRequest bad = InventoryDataFactory.seedAmounts(Map.of(9_999_999_999L, 1.0));
            Response response = inventoryFixture.conductInventoryRaw(flyPointId, UserRole.ADMIN, bad);
            assertThat(response.statusCode()).isBetween(400, 499);
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }

    @Test(priority = 90)
    @TestCaseId("TC-FLY-INV-NEG-04")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_INV_NEG_04)
    @Severity(SeverityLevel.MINOR)
    public void sequentialPutsOnFlyPointLastWriteWins() {
        inventoryFixture.openSession(flyPointId);
        try {
            inventoryFixture.setResourceAmount(flyPointId, UserRole.ADMIN, resourceId, ISSUE_AMOUNT + 1);
            inventoryFixture.setResourceAmount(flyPointId, UserRole.ADMIN, resourceId, ISSUE_AMOUNT + 7);
            assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                    .isCloseTo(ISSUE_AMOUNT + 7, within(0.01));
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
    }
}
