package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
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
 * Повернення виробів від екіпажу на склад (CPMA-647):
 * {@code POST /api/v1/relocations/receive} з sender=CREW.
 */
@Slf4j
@Epic("Relocation")
@Feature("Crew Return")
@Story("Receive from Crew")
public class CrewReturnTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-ret-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final double RETURN_AMOUNT = 5.0;
    /** Owner often lacks inventory-list::{crew}::read — crew/FP stock via ADMIN. */
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: ресурс і relocation context")
    public void setupCrewReturnTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        relocationFixture.ensureStock(owner1StorageId, resourceId, 100.0);
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-RET-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RET_001)
    @Severity(SeverityLevel.CRITICAL)
    public void unattachedCrewReturnDebitsCrewCreditsWarehouse() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-ret-u-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        Long warehouseId = scenario.memberStorageId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, warehouseId, crewId, resourceId, ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before return");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before return");

        RelocationResponse received = relocationFixture.createCrewReceive(
                UserRole.OWNER_1, crewId, warehouseId, resourceId, RETURN_AMOUNT);

        assertThat(received.getState())
                .as("Повернення від CREW має бути AUTO_FINISHED")
                .isEqualTo(RelocationState.AUTO_FINISHED);
        assertThat(received.getSender().getId()).isEqualTo(crewId);
        assertThat(received.getRecipient().getId()).isEqualTo(warehouseId);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after return");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after return");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeCrew, afterCrew, crewId, resourceId, RETURN_AMOUNT,
                "unattached CREW — списання з екіпажу");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeWarehouse, afterWarehouse, warehouseId, resourceId, RETURN_AMOUNT,
                "unattached — зарахування на склад локації");
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-RET-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RET_002)
    @Severity(SeverityLevel.CRITICAL)
    public void attachedCrewReturnDebitsFlyPointCreditsWarehouse() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("crew-ret-a-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        Long flyPointId = scenario.flyPoint().getId();
        Long warehouseId = scenario.memberStorageId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, warehouseId, crewId, resourceId, ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before return");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before return");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before return");

        assertThat(beforeFp.amountOf(resourceId))
                .as("після auto-forward залишок має бути на FLY_POINT")
                .isGreaterThanOrEqualTo(ISSUE_AMOUNT);
        assertThat(beforeCrew.amountOf(resourceId))
                .as("attached CREW shelf ≈ 0 після auto-forward")
                .isLessThan(0.01);

        RelocationResponse received = relocationFixture.createCrewReceive(
                UserRole.OWNER_1, crewId, warehouseId, resourceId, RETURN_AMOUNT);

        assertThat(received.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after return");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after return");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after return");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, resourceId, RETURN_AMOUNT,
                "attached — списання з точки вильоту (через CREW)");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId,
                "attached CREW — після ланцюга FP→CREW→склад shelf знову ≈ 0");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeWarehouse, afterWarehouse, warehouseId, resourceId, RETURN_AMOUNT,
                "attached — зарахування на склад локації");
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-RET-003")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RET_003)
    @Severity(SeverityLevel.NORMAL)
    public void unattachedCrewReturnOverStockRejected() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-ret-neg-u-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        Long warehouseId = scenario.memberStorageId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, warehouseId, crewId, resourceId, ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before overstock");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before overstock");

        double overAmount = ISSUE_AMOUNT + 1.0;
        Response response = relocationFixture.tryCrewReceive(
                UserRole.OWNER_1, crewId, warehouseId, resourceId, overAmount);

        assertThat(response.statusCode())
                .as("повернення більше залишку екіпажу → 400")
                .isEqualTo(400);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after overstock");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after overstock");

        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId, "overstock — CREW без змін");
        RelocationStockAssertions.assertUnchanged(
                beforeWarehouse, afterWarehouse, warehouseId, resourceId, "overstock — склад без змін");
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-RET-004")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_RET_004)
    @Severity(SeverityLevel.NORMAL)
    public void attachedCrewReturnOverStockOnFlyPointRejected() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("crew-ret-neg-a-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        Long flyPointId = scenario.flyPoint().getId();
        Long warehouseId = scenario.memberStorageId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, warehouseId, crewId, resourceId, ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before overstock");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before overstock");

        double overAmount = ISSUE_AMOUNT + 1.0;
        Response response = relocationFixture.tryCrewReceive(
                UserRole.OWNER_1, crewId, warehouseId, resourceId, overAmount);

        assertThat(response.statusCode())
                .as("повернення більше залишку точки вильоту → 400")
                .isEqualTo(400);

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after overstock");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, warehouseId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after overstock");

        RelocationStockAssertions.assertUnchanged(
                beforeFp, afterFp, flyPointId, resourceId, "overstock — FLY_POINT без змін");
        RelocationStockAssertions.assertUnchanged(
                beforeWarehouse, afterWarehouse, warehouseId, resourceId, "overstock — склад без змін");
    }
}
