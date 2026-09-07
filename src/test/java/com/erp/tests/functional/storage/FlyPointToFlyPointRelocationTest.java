package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Передача між точками зльоту: FLY_POINT → FLY_POINT (REQ-CREW-002 AC-23).
 */
@Slf4j
@Epic("Relocation")
@Feature("Fly Point to Fly Point")
@Story("Send FLY_POINT → FLY_POINT")
public class FlyPointToFlyPointRelocationTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "fly-fp2fp-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;
    /** Send from FLY_POINT requires {@code relocation::{sender}::create}; OWNER_1 has it on warehouse only. */
    private static final UserRole FP_SENDER = UserRole.ADMIN;

    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: ресурс і relocation context")
    public void setupFlyPointToFlyPointTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureWarehouseStock() {
        relocationFixture.ensureStock(owner1StorageId, resourceId, 100.0);
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @Test(priority = 10)
    @TestCaseId("TC-FLY-FP-001")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_FP_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendBetweenFlyPointsCreatedThenFinishedBySender() {
        CrewRegionScenario scenario = crewFixture.prepareTwoFlyPointsScenario("fp2fp-ok-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long fpA = scenario.flyPoint().getId();
        Long fpB = scenario.flyPointB().getId();
        Long warehouseId = scenario.memberStorageId();

        seedStockOnFlyPoint(fpA);

        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1,
                Set.of(resourceId), "warehouse before FP→FP");
        ProductionStockAssertions.StockSnapshot beforeFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER,
                Set.of(resourceId), "FP_A before send");
        ProductionStockAssertions.StockSnapshot beforeFpB = RelocationStockAssertions.capture(
                apiExecutor, fpB, STOCK_READER,
                Set.of(resourceId), "FP_B before send");

        RelocationResponse sent = relocationFixture.createSend(
                FP_SENDER, fpA, fpB, resourceId, ISSUE_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot afterSendFpB = RelocationStockAssertions.capture(
                apiExecutor, fpB, STOCK_READER,
                Set.of(resourceId), "FP_B after send");
        RelocationStockAssertions.assertUnchanged(
                beforeFpB, afterSendFpB, fpB, resourceId,
                "поки CREATED — отримувач FLY_POINT без зарахування");

        RelocationResponse finished = relocationFixture.resolve(
                FP_SENDER, sent.getId(), fpA, RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1,
                Set.of(resourceId), "warehouse after finish");
        ProductionStockAssertions.StockSnapshot afterFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER,
                Set.of(resourceId), "FP_A after finish");
        ProductionStockAssertions.StockSnapshot afterFpB = RelocationStockAssertions.capture(
                apiExecutor, fpB, STOCK_READER,
                Set.of(resourceId), "FP_B after finish");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeFpA, afterFpA, fpA, resourceId, ISSUE_AMOUNT,
                "FP_A → FP_B списання з точки-відправника");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFpB, afterFpB, fpB, resourceId, ISSUE_AMOUNT,
                "FP_A → FP_B зарахування на точку-отримувача");
        RelocationStockAssertions.assertUnchanged(
                beforeWarehouse, afterWarehouse, warehouseId, resourceId,
                "склад локації ізольований від FP→FP");
    }

    @Test(priority = 20)
    @TestCaseId("TC-FLY-FP-002")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_FP_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendFromFlyPointToUnitOrCrewRejected() {
        CrewRegionScenario scenario = crewFixture.prepareTwoFlyPointsScenario("fp2fp-neg-");
        StorageResponse crew = storageFixture.createCrewStorage(
                scenario.unit().getId(), "fp2fp-neg-crew-");
        regionFixture.addRegionLocations(scenario.region().getId(), crew.getId());
        refreshRoleSessions(UserRole.OWNER_1);

        Long fpA = scenario.flyPoint().getId();
        Long unitId = scenario.unit().getId();
        Long crewId = crew.getId();

        seedStockOnFlyPoint(fpA);

        ProductionStockAssertions.StockSnapshot beforeFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER, Set.of(resourceId), "FP_A before rejected sends");
        ProductionStockAssertions.StockSnapshot beforeUnit = RelocationStockAssertions.capture(
                apiExecutor, unitId, STOCK_READER, Set.of(resourceId), "UNIT before rejected sends");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "CREW before rejected sends");

        Response toUnit = relocationFixture.sendRaw(
                FP_SENDER,
                RelocationDataFactory.buildSendRequest(fpA, unitId, resourceId, ISSUE_AMOUNT));
        assertThat(toUnit.statusCode())
                .as("FLY_POINT → UNIT має бути відхилено")
                .isBetween(400, 499);

        Response toCrew = relocationFixture.sendRaw(
                FP_SENDER,
                RelocationDataFactory.buildSendRequest(fpA, crewId, resourceId, ISSUE_AMOUNT));
        assertThat(toCrew.statusCode())
                .as("FLY_POINT → CREW має бути відхилено")
                .isBetween(400, 499);

        ProductionStockAssertions.StockSnapshot afterFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER, Set.of(resourceId), "FP_A after rejected sends");
        ProductionStockAssertions.StockSnapshot afterUnit = RelocationStockAssertions.capture(
                apiExecutor, unitId, STOCK_READER, Set.of(resourceId), "UNIT after rejected sends");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "CREW after rejected sends");

        RelocationStockAssertions.assertUnchanged(
                beforeFpA, afterFpA, fpA, resourceId, "відхилений send не списує FP_A");
        RelocationStockAssertions.assertUnchanged(
                beforeUnit, afterUnit, unitId, resourceId, "відхилений send не чіпає UNIT");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId, "відхилений send не чіпає CREW");
    }

    private void seedStockOnFlyPoint(Long flyPointId) {
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                owner1StorageId,
                flyPointId,
                resourceId,
                ISSUE_AMOUNT);
    }
}
