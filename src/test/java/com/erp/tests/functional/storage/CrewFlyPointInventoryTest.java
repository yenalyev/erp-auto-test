package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Інвентаризація CREW: unattached змінює CREW; attached PUT на crewId проксує на FLY_POINT.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Crew / Fly Point Inventory Proxy")
public class CrewFlyPointInventoryTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-fp-inv-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final double TARGET_AMOUNT = 18.0;

    private Long openSessionStorageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка fixtures для crew/fly-point inventory proxy")
    public void setupCrewFlyPointInventoryTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
    }

    @AfterMethod(alwaysRun = true)
    public void closeOpenedSessions() {
        if (openSessionStorageId != null) {
            try {
                inventoryFixture.ensureClosed(openSessionStorageId);
            } catch (Exception e) {
                log.warn("Session cleanup failed for {}: {}", openSessionStorageId, e.getMessage());
            }
            openSessionStorageId = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-INV-011")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_011)
    @Severity(SeverityLevel.CRITICAL)
    public void unattachedCrewInventoryDoesNotChangeSiblingFlyPoint() {
        CrewRegionScenario crewScenario = crewFixture.prepareSingleCrewScenario("crew-unatt-");
        StorageResponse siblingFp = storageFixture.createFlyPointStorage(
                crewScenario.unit().getId(), "crew-unatt-fp-");
        regionFixture.addRegionLocations(crewScenario.region().getId(), siblingFp.getId());

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "u-");
        long resourceId = resource.getId();
        long crewId = crewScenario.crew().getId();

        relocationFixture.ensureStock(crewScenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                crewScenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        double fpBefore = relocationFixture.getResourceStock(siblingFp.getId(), resourceId, UserRole.ADMIN);

        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(crewId);
        openSessionStorageId = crewId;
        try {
            inventoryFixture.setResourceAmount(crewId, UserRole.ADMIN, resourceId, TARGET_AMOUNT);
            assertThat(relocationFixture.getResourceStock(crewId, resourceId, UserRole.ADMIN))
                    .as("Unattached CREW: stock оновлюється на екіпажі")
                    .isCloseTo(TARGET_AMOUNT, within(0.01));
            assertThat(relocationFixture.getResourceStock(siblingFp.getId(), resourceId, UserRole.ADMIN))
                    .as("Sibling FLY_POINT не змінюється")
                    .isCloseTo(fpBefore, within(0.01));
        } finally {
            inventoryFixture.closeSession(crewId);
            openSessionStorageId = null;
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-INV-012")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_012)
    @Severity(SeverityLevel.CRITICAL)
    public void attachedCrewInventoryPutProxiesToFlyPoint() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("crew-att-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "a-");
        long resourceId = resource.getId();
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                .as("Після видачі на attached CREW stock на FLY_POINT")
                .isCloseTo(ISSUE_AMOUNT, within(0.01));

        // Сесія на FP (ціль proxy) і на CREW (id у URL PUT)
        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(flyPointId);
        inventoryFixture.openSession(crewId);
        openSessionStorageId = crewId;
        try {
            inventoryFixture.setResourceAmount(crewId, UserRole.ADMIN, resourceId, TARGET_AMOUNT);

            assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                    .as("Attached: PUT на crewId проксує — stock змінюється на FLY_POINT")
                    .isCloseTo(TARGET_AMOUNT, within(0.01));
            assertThat(relocationFixture.getResourceStock(crewId, resourceId, UserRole.ADMIN))
                    .as("Attached: окремий shelf CREW не отримує target amount")
                    .isLessThan(TARGET_AMOUNT - 0.5);
        } finally {
            inventoryFixture.ensureClosed(crewId);
            inventoryFixture.ensureClosed(flyPointId);
            openSessionStorageId = null;
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-INV-013")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INV_013)
    @Severity(SeverityLevel.CRITICAL)
    public void attachedCrewPutEquivalentToDirectFlyPointPut() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("crew-eq-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "e-");
        long resourceId = resource.getId();
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                flyPointId,
                resourceId,
                ISSUE_AMOUNT);

        double viaFpTarget = ISSUE_AMOUNT + 3.0;
        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.openSession(flyPointId);
        try {
            inventoryFixture.setResourceAmount(flyPointId, UserRole.ADMIN, resourceId, viaFpTarget);
        } finally {
            inventoryFixture.closeSession(flyPointId);
        }
        double afterDirectFp = relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN);

        double viaCrewTarget = viaFpTarget + 4.0;
        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.ensureClosed(crewId);
        inventoryFixture.openSession(flyPointId);
        inventoryFixture.openSession(crewId);
        openSessionStorageId = crewId;
        try {
            inventoryFixture.setResourceAmount(crewId, UserRole.ADMIN, resourceId, viaCrewTarget);
            double afterProxy = relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN);
            assertThat(afterProxy)
                    .as("PUT на crewId дає той самий ефект на FP, що й прямий PUT")
                    .isCloseTo(viaCrewTarget, within(0.01));
            assertThat(afterDirectFp)
                    .as("Контроль: прямий PUT на FP працює")
                    .isCloseTo(viaFpTarget, within(0.01));
        } finally {
            inventoryFixture.ensureClosed(crewId);
            inventoryFixture.ensureClosed(flyPointId);
            openSessionStorageId = null;
        }
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-OWN-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_OWN_001)
    @Severity(SeverityLevel.CRITICAL)
    public void afterAttachCrewStockVisibleOnFlyPointInventory() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("own-att-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "own1-");
        long resourceId = resource.getId();
        long crewId = scenario.crew().getId();

        StorageResponse flyPoint = storageFixture.createFlyPointStorage(
                scenario.unit().getId(), "own-att-fp-");
        regionFixture.addRegionLocations(scenario.region().getId(), flyPoint.getId());

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        storageFixture.reparent(UserRole.ADMIN, crewId, flyPoint.getId());

        assertThat(relocationFixture.getResourceStock(flyPoint.getId(), resourceId, UserRole.ADMIN))
                .as("Після attach stock на FLY_POINT")
                .isCloseTo(ISSUE_AMOUNT, within(0.01));

        inventoryFixture.ensureClosed(flyPoint.getId());
        inventoryFixture.openSession(flyPoint.getId());
        openSessionStorageId = flyPoint.getId();
        try {
            assertThat(inventoryFixture.getResourceStock(flyPoint.getId(), resourceId, UserRole.ADMIN))
                    .as("Inventory session на FP бачить ресурс після attach")
                    .isCloseTo(ISSUE_AMOUNT, within(0.01));
        } finally {
            inventoryFixture.closeSession(flyPoint.getId());
            openSessionStorageId = null;
        }
    }

    @Test(priority = 50)
    @TestCaseId("TC-CREW-OWN-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_OWN_002)
    @Severity(SeverityLevel.CRITICAL)
    public void afterAttachedIssueInventorySeesStockOnFlyPointNotCrew() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("own-fwd-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "own2-");
        long resourceId = resource.getId();
        long crewId = scenario.crew().getId();
        long flyPointId = scenario.flyPoint().getId();

        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        assertThat(relocationFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                .as("Auto-forward: stock на FP")
                .isCloseTo(ISSUE_AMOUNT, within(0.01));
        assertThat(relocationFixture.getResourceStock(crewId, resourceId, UserRole.ADMIN))
                .as("Auto-forward: CREW без залишку")
                .isLessThan(0.01);

        inventoryFixture.ensureClosed(flyPointId);
        inventoryFixture.openSession(flyPointId);
        openSessionStorageId = flyPointId;
        try {
            assertThat(inventoryFixture.getResourceStock(flyPointId, resourceId, UserRole.ADMIN))
                    .isCloseTo(ISSUE_AMOUNT, within(0.01));
        } finally {
            inventoryFixture.closeSession(flyPointId);
            openSessionStorageId = null;
        }
    }
}
