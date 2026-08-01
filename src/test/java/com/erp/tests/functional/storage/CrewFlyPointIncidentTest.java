package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.IncidentFixture;
import com.erp.fixtures.InventoryFixture;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Гілка «Надзвичайна подія» для видачі на CREW / FLY_POINT.
 */
@Slf4j
@Epic("Relocation")
@Feature("Incident — надзвичайна подія")
@Story("CREW / FLY_POINT incident")
public class CrewFlyPointIncidentTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-inc-";
    private static final double ISSUE_AMOUNT = 7.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;
    /** Staging OWNER_1 may lack {@code incident::create}; ADMIN creates/deletes incidents. */
    private static final UserRole INCIDENT_ACTOR = UserRole.ADMIN;

    private IncidentFixture incidentFixture;
    private InventoryFixture inventoryFixture;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: incident + resource")
    public void setupCrewFlyPointIncidentTests() {
        incidentFixture = new IncidentFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
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
    @TestCaseId("TC-CREW-INC-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testIncidentOnSendToCrewDoesNotCreditCrew() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-inc-");
        refreshRoleSessions(UserRole.OWNER_1);

        String marker = IncidentDataFactory.uniqueDescription();
        Long crewId = scenario.crew().getId();
        double senderIncidentBefore = incidentSummaryAmount(
                scenario.memberStorageId(), resourceId, UserRole.OWNER_1);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before incident");

        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT,
                marker);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        incidentFixture.createIncident(INCIDENT_ACTOR, sent, marker);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, scenario.memberStorageId(), marker);
        assertThat(lost).isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after incident");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId,
                "після надзвичайної події CREW без зарахування");

        Response senderHistory = inventoryFixture.getOperationHistoryToday(
                scenario.memberStorageId(), UserRole.OWNER_1);
        assertThat(senderHistory.statusCode()).isEqualTo(200);
        assertThat(senderHistory.getBody().asString()).contains("INCIDENT_WRITE_OFF");
        assertThat(incidentSummaryAmount(senderHistory, resourceId) - senderIncidentBefore)
                .isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-INC-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testIncidentOnSendToFlyPointDoesNotCreditFlyPoint() {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario("fp-inc-");
        refreshRoleSessions(UserRole.OWNER_1);

        String marker = IncidentDataFactory.uniqueDescription();
        Long flyPointId = scenario.flyPoint().getId();

        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before incident");

        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                flyPointId,
                resourceId,
                ISSUE_AMOUNT,
                marker);
        incidentFixture.createIncident(INCIDENT_ACTOR, sent, marker);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, scenario.memberStorageId(), marker);
        assertThat(lost).isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after incident");
        RelocationStockAssertions.assertUnchanged(
                beforeFp, afterFp, flyPointId, resourceId,
                "після надзвичайної події FLY_POINT без зарахування");
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-INC-003")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testLostCrewRelocationCannotBeFinishedBySender() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-inc-fin-");
        refreshRoleSessions(UserRole.OWNER_1);

        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT,
                marker);
        incidentFixture.createIncident(INCIDENT_ACTOR, sent, marker);

        Response denied = relocationFixture.resolveRaw(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(denied.statusCode())
                .as("LOST не можна підтвердити (FINISHED)")
                .isBetween(400, 499);
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-INC-004")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_004)
    @Severity(SeverityLevel.CRITICAL)
    public void testFinishedCrewRelocationCannotCreateIncident() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-inc-done-");
        refreshRoleSessions(UserRole.OWNER_1);

        RelocationResponse finished = relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        var request = IncidentDataFactory.buildFullCargoLoss(
                finished, IncidentDataFactory.uniqueDescription());
        Response response = apiExecutor.executeIncidentCreate(request, INCIDENT_ACTOR);
        assertThat(response.statusCode())
                .as("Incident лише для CREATED (staging може віддати 4xx або 500)")
                .isGreaterThanOrEqualTo(400);
    }

    @Test(priority = 50)
    @TestCaseId("TC-CREW-INC-005")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_005)
    @Severity(SeverityLevel.NORMAL)
    public void testDeleteIncidentThenFinishCreditsCrew() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-inc-rest-");
        refreshRoleSessions(UserRole.OWNER_1);

        String marker = IncidentDataFactory.uniqueDescription();
        Long crewId = scenario.crew().getId();

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before restore");

        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT,
                marker);
        incidentFixture.createIncident(INCIDENT_ACTOR, sent, marker);
        incidentFixture.deleteIncident(INCIDENT_ACTOR, sent.getId());

        RelocationResponse restored = relocationFixture.findInTransitByDescription(
                UserRole.OWNER_1, scenario.memberStorageId(), marker);
        assertThat(restored).isNotNull();
        assertThat(restored.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after restore+finish");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterCrew, crewId, resourceId, ISSUE_AMOUNT,
                "після delete incident + FINISHED — credit на CREW");
    }

    @Test(priority = 60)
    @TestCaseId("TC-CREW-INC-006")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_INC_006)
    @Severity(SeverityLevel.NORMAL)
    public void testIncidentOnAttachedCrewDoesNotAutoForwardToFlyPoint() {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("crew-inc-att-");
        refreshRoleSessions(UserRole.OWNER_1);

        String marker = IncidentDataFactory.uniqueDescription();
        Long flyPointId = scenario.flyPoint().getId();

        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before incident");

        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT,
                marker);
        incidentFixture.createIncident(INCIDENT_ACTOR, sent, marker);

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after incident");
        RelocationStockAssertions.assertUnchanged(
                beforeFp, afterFp, flyPointId, resourceId,
                "incident до FINISHED — auto-forward на FLY_POINT не спрацьовує");
    }

    private double incidentSummaryAmount(long storageId, long resourceId, UserRole role) {
        Response history = inventoryFixture.getOperationHistoryToday(storageId, role);
        assertThat(history.statusCode()).isEqualTo(200);
        return incidentSummaryAmount(history, resourceId);
    }

    private static double incidentSummaryAmount(Response history, long resourceId) {
        var entries = history.jsonPath().getList("totalIncidentResources");
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < entries.size(); i++) {
            Long id = history.jsonPath().getLong("totalIncidentResources[" + i + "].resource.id");
            if (id != null && id == resourceId) {
                Number amount = history.jsonPath().get("totalIncidentResources[" + i + "].amount");
                return amount != null ? amount.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }
}
