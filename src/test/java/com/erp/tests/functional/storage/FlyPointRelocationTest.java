package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
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
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Видача на точку вильоту та екіпаж, прикріплений до FLY_POINT.
 */
@Slf4j
@Epic("Relocation")
@Feature("Fly Point Issuance")
@Story("Send to FLY_POINT / attached CREW")
public class FlyPointRelocationTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "fly-rel-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: ресурс і relocation context")
    public void setupFlyPointRelocationTests() {
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
    @TestCaseId("TC-FLY-REL-001")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_REL_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendToFlyPointCreatedThenFinishedBySender() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("fly-direct-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long flyPointId = scenario.flyPoint().getId();
        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before send to fly point");
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER,
                Set.of(resourceId), "fly point before send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                flyPointId,
                resourceId,
                ISSUE_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        ProductionStockAssertions.StockSnapshot afterSendFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER,
                Set.of(resourceId), "fly point after send");
        RelocationStockAssertions.assertUnchanged(
                beforeFp, afterSendFp, flyPointId, resourceId,
                "поки CREATED — точка вильоту без зарахування");

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after finish sender");
        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER,
                Set.of(resourceId), "fly point after finish");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSender, scenario.memberStorageId(), resourceId, ISSUE_AMOUNT,
                "send на FLY_POINT");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFp, afterFp, flyPointId, resourceId, ISSUE_AMOUNT,
                "зарахування на точку вильоту після підтвердження");
    }

    @Test(priority = 20)
    @TestCaseId("TC-FLY-REL-002")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_REL_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testAttachedCrewFinishAutoForwardsToFlyPoint() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("fly-att-");
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        Long flyPointId = scenario.flyPoint().getId();

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before");
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after finish");
        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after finish");

        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId,
                "attached CREW — залишок не лишається на екіпажі (auto-forward)");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFp, afterFp, flyPointId, resourceId, ISSUE_AMOUNT,
                "auto CREW→FLY_POINT після FINISHED");
    }

    @Test(priority = 30)
    @TestCaseId("TC-FLY-REL-003")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_REL_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewToFlyPointIsAutoFinished() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("fly-c2fp-");
        StorageResponse flyPoint = storageFixture.createFlyPointStorage(
                scenario.unit().getId(), "fly-c2fp-fp-");
        regionFixture.addRegionLocations(scenario.region().getId(), flyPoint.getId());
        refreshRoleSessions(UserRole.OWNER_1);

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), STOCK_READER,
                Set.of(resourceId), "crew before CREW→FP");
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPoint.getId(), STOCK_READER,
                Set.of(resourceId), "fp before CREW→FP");

        RelocationResponse relocation = relocationFixture.createSend(
                UserRole.ADMIN,
                scenario.crew().getId(),
                flyPoint.getId(),
                resourceId,
                ISSUE_AMOUNT);

        assertThat(relocation.getState())
                .as("Пряме CREW→FLY_POINT має бути миттєвим AUTO_FINISHED")
                .isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), STOCK_READER,
                Set.of(resourceId), "crew after CREW→FP");
        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPoint.getId(), STOCK_READER,
                Set.of(resourceId), "fp after CREW→FP");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId, ISSUE_AMOUNT,
                "CREW→FLY_POINT списання з екіпажу");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFp, afterFp, flyPoint.getId(), resourceId, ISSUE_AMOUNT,
                "CREW→FLY_POINT зарахування на точку");
    }

    @Test(priority = 40)
    @TestCaseId("TC-FLY-REL-004")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_REL_004)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewReparentBetweenFlyPointsMovesStockOnAttach() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("fly-reparent-");
        StorageResponse flyPointA = storageFixture.createFlyPointStorage(
                scenario.unit().getId(), "fly-reparent-a-");
        StorageResponse flyPointB = storageFixture.createFlyPointStorage(
                scenario.unit().getId(), "fly-reparent-b-");
        regionFixture.addRegionLocations(
                scenario.region().getId(), flyPointA.getId(), flyPointB.getId());
        refreshRoleSessions(UserRole.OWNER_1);

        Long crewId = scenario.crew().getId();
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before attach");
        ProductionStockAssertions.StockSnapshot beforeFpA = RelocationStockAssertions.capture(
                apiExecutor, flyPointA.getId(), STOCK_READER, Set.of(resourceId), "fpA before attach");

        StorageResponse attached = storageFixture.reparent(UserRole.ADMIN, crewId, flyPointA.getId());
        assertThat(attached.getParent()).isNotNull();
        assertThat(attached.getParent().getId())
                .as("Екіпаж прикріплений лише до однієї точки (FP_A)")
                .isEqualTo(flyPointA.getId());

        ProductionStockAssertions.StockSnapshot afterAttachCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after attach FP_A");
        ProductionStockAssertions.StockSnapshot afterAttachFpA = RelocationStockAssertions.capture(
                apiExecutor, flyPointA.getId(), STOCK_READER, Set.of(resourceId), "fpA after attach");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeCrew, afterAttachCrew, crewId, resourceId, ISSUE_AMOUNT,
                "прикріплення до FLY_POINT списує залишок з CREW");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFpA, afterAttachFpA, flyPointA.getId(), resourceId, ISSUE_AMOUNT,
                "прикріплення зараховує залишок на FLY_POINT");

        StorageResponse reparented = storageFixture.reparent(UserRole.ADMIN, crewId, flyPointB.getId());
        assertThat(reparented.getParent()).isNotNull();
        assertThat(reparented.getParent().getId())
                .as("Після reparent екіпаж має рівно одного parent — FP_B")
                .isEqualTo(flyPointB.getId());

        StorageResponse refreshed = storageFixture.getById(UserRole.ADMIN, crewId);
        assertThat(refreshed.getParent().getId()).isEqualTo(flyPointB.getId());

        List<StorageResponse> underA = crewFixture.getCrewNames(UserRole.ADMIN, flyPointA.getId(), null);
        List<StorageResponse> underB = crewFixture.getCrewNames(UserRole.ADMIN, flyPointB.getId(), null);
        assertThat(underA).extracting(StorageResponse::getId).doesNotContain(crewId);
        assertThat(underB).extracting(StorageResponse::getId).contains(crewId);
    }

    @Test(priority = 50)
    @TestCaseId("TC-FLY-REL-005")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_REL_005)
    @Severity(SeverityLevel.CRITICAL)
    public void testMultipleCrewsUnderSameFlyPointAutoForward() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("fly-multi-");
        StorageResponse crew2 = storageFixture.createCrewStorage(
                scenario.flyPoint().getId(), "fly-multi-crew2-");
        regionFixture.addRegionLocations(scenario.region().getId(), crew2.getId());
        refreshRoleSessions(UserRole.OWNER_1);

        Long flyPointId = scenario.flyPoint().getId();
        Long crew1Id = scenario.crew().getId();
        Long crew2Id = crew2.getId();

        ProductionStockAssertions.StockSnapshot beforeCrew1 = RelocationStockAssertions.capture(
                apiExecutor, crew1Id, STOCK_READER, Set.of(resourceId), "crew1 before");
        ProductionStockAssertions.StockSnapshot beforeCrew2 = RelocationStockAssertions.capture(
                apiExecutor, crew2Id, STOCK_READER, Set.of(resourceId), "crew2 before");
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before multi issue");

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crew1Id,
                resourceId,
                ISSUE_AMOUNT);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crew2Id,
                resourceId,
                ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot afterCrew1 = RelocationStockAssertions.capture(
                apiExecutor, crew1Id, STOCK_READER, Set.of(resourceId), "crew1 after");
        ProductionStockAssertions.StockSnapshot afterCrew2 = RelocationStockAssertions.capture(
                apiExecutor, crew2Id, STOCK_READER, Set.of(resourceId), "crew2 after");
        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after multi");

        RelocationStockAssertions.assertUnchanged(
                beforeCrew1, afterCrew1, crew1Id, resourceId,
                "crew1 — залишок не лишається (auto-forward)");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew2, afterCrew2, crew2Id, resourceId,
                "crew2 — залишок не лишається (auto-forward)");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFp, afterFp, flyPointId, resourceId, ISSUE_AMOUNT * 2,
                "обидва екіпажі auto-forward на одну FLY_POINT");

        List<StorageResponse> crewsOnPoint = crewFixture.getCrewNames(
                UserRole.ADMIN, flyPointId, null);
        assertThat(crewsOnPoint)
                .extracting(StorageResponse::getId)
                .contains(crew1Id, crew2Id);
    }
}
