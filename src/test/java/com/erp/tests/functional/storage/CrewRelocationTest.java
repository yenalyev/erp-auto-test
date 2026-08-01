package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Видача ресурсів на екіпаж (unattached CREW):
 * send → CREATED («В дорозі») → підтвердження відправником → FINISHED.
 */
@Slf4j
@Epic("Relocation")
@Feature("Crew Issuance")
@Story("Send to Crew")
public class CrewRelocationTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-rel-";
    private static final double ISSUE_AMOUNT = 15.0;
    /** Owner lacks {@code inventory-list::{crew}::read} in Keycloak — crew stock via ADMIN. */
    private static final UserRole CREW_STOCK_READER = UserRole.ADMIN;

    private CrewRegionScenario scenario;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: область CREWS, ресурс, stock")
    public void setupCrewRelocationTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        scenario = crewFixture.prepareSingleCrewScenario("crew-rel-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        refreshRoleSessions(UserRole.OWNER_1, UserRole.OWNER_2);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-REL-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendToCrewCreatedThenFinishedBySender() {
        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew before send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(sent.getRecipient().getId()).isEqualTo(scenario.crew().getId());

        ProductionStockAssertions.StockSnapshot afterSendSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after send sender");
        ProductionStockAssertions.StockSnapshot afterSendCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew after send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSendSender, scenario.memberStorageId(), resourceId, ISSUE_AMOUNT,
                "видача на екіпаж — списання при CREATED");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterSendCrew, scenario.crew().getId(), resourceId,
                "поки CREATED — екіпаж без зарахування");

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);

        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterFinishCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew after finish");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterFinishCrew, scenario.crew().getId(), resourceId, ISSUE_AMOUNT,
                "зарахування на екіпаж після підтвердження відправником");
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-REL-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_002)
    @Severity(SeverityLevel.NORMAL)
    public void testCrewRelocationVisibleInJournal() {
        String marker = "crew-rel-journal-" + System.currentTimeMillis();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT,
                marker);

        RelocationResponse inTransit = relocationFixture.findInTransitByDescription(
                UserRole.OWNER_1, scenario.memberStorageId(), marker);
        assertThat(inTransit).as("Після send — у журналі «В дорозі»").isNotNull();
        assertThat(inTransit.getId()).isEqualTo(sent.getId());
        assertThat(inTransit.getState()).isEqualTo(RelocationState.CREATED);

        relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);

        RelocationJournalQuery query = RelocationJournalQuery.sentHistoryUi(scenario.memberStorageId())
                .toBuilder()
                .productId(resourceId)
                .pageSize(50)
                .build();

        List<RelocationResponse> page = relocationFixture.getJournalPage(query, UserRole.OWNER_1);
        assertThat(page.stream()
                .filter(r -> r.getRecipient() != null
                        && scenario.crew().getId().equals(r.getRecipient().getId()))
                .map(RelocationResponse::getId))
                .contains(sent.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-CREW-REL-003")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_003)
    @Severity(SeverityLevel.NORMAL)
    public void testSendToCrewInsufficientStock() {
        double currentStock = relocationFixture.getResourceStock(
                scenario.memberStorageId(), resourceId, UserRole.OWNER_1);
        double excessiveAmount = currentStock + 1000.0;

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before failed send");

        var request = RelocationDataFactory.buildSendRequest(
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                excessiveAmount);
        var response = relocationFixture.sendRaw(UserRole.OWNER_1, request);

        assertThat(response.statusCode()).isBetween(400, 499);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after failed send");
        RelocationStockAssertions.assertUnchanged(
                before, after, scenario.memberStorageId(), resourceId,
                "недостатній stock — залишок без змін");
    }

    @Test(priority = 40)
    @TestCaseId("TC-CREW-REL-004")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_004)
    @Severity(SeverityLevel.CRITICAL)
    public void testMultiResourceSendToCrewCreatedThenFinished() {
        ResourceResponse resource2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "b-");
        Long resourceId2 = resource2.getId();
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId2, 100.0);

        double amount1 = 10.0;
        double amount2 = 8.0;

        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "before multi send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId, resourceId2), "crew before multi send");

        List<ResourceUsageRequest> items = List.of(
                RelocationDataFactory.usage(resourceId, amount1),
                RelocationDataFactory.usage(resourceId2, amount2));
        RelocationOutputRequest request = RelocationDataFactory.buildSendMultiItem(
                scenario.memberStorageId(), scenario.crew().getId(), items);
        Response sendResponse = relocationFixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(sendResponse.statusCode()).isBetween(200, 299);
        RelocationResponse sent = sendResponse.as(RelocationResponse.class);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot afterSendSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "after multi send");
        ProductionStockAssertions.StockSnapshot afterSendCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId, resourceId2), "crew after multi send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSendSender, scenario.memberStorageId(), resourceId, amount1,
                "multi send resource 1");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSendSender, scenario.memberStorageId(), resourceId2, amount2,
                "multi send resource 2");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterSendCrew, scenario.crew().getId(), resourceId,
                "multi send — crew без credit до finish");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterSendCrew, scenario.crew().getId(), resourceId2,
                "multi send — crew без credit до finish (res2)");

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterFinishCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId, resourceId2), "crew after multi finish");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterFinishCrew, scenario.crew().getId(), resourceId, amount1,
                "multi send crew resource 1");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterFinishCrew, scenario.crew().getId(), resourceId2, amount2,
                "multi send crew resource 2");
    }

    @Test(priority = 50)
    @TestCaseId("TC-CREW-REL-005")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_005)
    @Severity(SeverityLevel.CRITICAL)
    public void testOwner2CannotSendToCrewOutsideRegion() {
        var request = RelocationDataFactory.buildSendRequest(
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before denied crew send");

        var response = relocationFixture.sendRaw(UserRole.OWNER_2, request);
        assertThat(response.statusCode()).isIn(403, 404);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after denied crew send");
        RelocationStockAssertions.assertUnchanged(
                before, after, scenario.memberStorageId(), resourceId,
                "OWNER_2 поза CREWS — stock без змін");
    }

    @Test(priority = 60)
    @TestCaseId("TC-CREW-REL-006")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_006)
    @Severity(SeverityLevel.NORMAL)
    public void testSendToCrewFromProductionSender() {
        // Isolated CREWS scenario — do not mutate class-shared scenario.region() under suite load.
        CrewRegionScenario prodScenario = crewFixture.prepareSingleCrewScenario("crew-prod-rel-");
        StorageResponse production = storageFixture.createChildStorage(
                prodScenario.unit().getId(),
                "crew-prod-",
                UnitType.PRODUCTION,
                StorageRelation.INTERNAL);
        regionFixture.addRegionLocations(prodScenario.region().getId(), production.getId());
        relocationFixture.ensureStock(production.getId(), resourceId, 100.0, UserRole.ADMIN);
        refreshRoleSessions(UserRole.OWNER_1);

        ProductionStockAssertions.StockSnapshot beforeProd = RelocationStockAssertions.capture(
                apiExecutor, production.getId(), UserRole.ADMIN,
                Set.of(resourceId), "production before send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, prodScenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew before production send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN,
                production.getId(),
                prodScenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot afterSendProd = RelocationStockAssertions.capture(
                apiExecutor, production.getId(), UserRole.ADMIN,
                Set.of(resourceId), "production after send");
        ProductionStockAssertions.StockSnapshot afterSendCrew = RelocationStockAssertions.capture(
                apiExecutor, prodScenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew after production send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeProd, afterSendProd, production.getId(), resourceId, ISSUE_AMOUNT,
                "PRODUCTION sender");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterSendCrew, prodScenario.crew().getId(), resourceId,
                "crew без credit до finish (PRODUCTION)");

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.ADMIN, sent.getId(), production.getId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterFinishCrew = RelocationStockAssertions.capture(
                apiExecutor, prodScenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew after PRODUCTION finish");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterFinishCrew, prodScenario.crew().getId(), resourceId, ISSUE_AMOUNT,
                "crew after PRODUCTION finish");
    }

    @Test(priority = 70)
    @TestCaseId("TC-CREW-REL-007")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_007)
    @Severity(SeverityLevel.NORMAL)
    public void testCrewRelocationVisibleInRecipientJournal() {
        RelocationResponse finished = relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        RelocationJournalQuery query = RelocationJournalQuery.receivedHistoryUi(scenario.crew().getId())
                .toBuilder()
                .productId(resourceId)
                .pageSize(50)
                .build();

        List<RelocationResponse> page = relocationFixture.getJournalPage(query, CREW_STOCK_READER);
        assertThat(page.stream().map(RelocationResponse::getId)).contains(finished.getId());
    }

    @Test(priority = 80)
    @TestCaseId("TC-CREW-REL-008")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_008)
    @Severity(SeverityLevel.NORMAL)
    public void testMultiResourceSendWithinStockLimits() {
        ResourceResponse resource2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "lim-");
        Long resourceId2 = resource2.getId();
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId2, 50.0);

        double stock1 = relocationFixture.getResourceStock(
                scenario.memberStorageId(), resourceId, UserRole.OWNER_1);
        double stock2 = relocationFixture.getResourceStock(
                scenario.memberStorageId(), resourceId2, UserRole.OWNER_1);

        List<ResourceUsageRequest> items = List.of(
                RelocationDataFactory.usage(resourceId, stock1 - 1),
                RelocationDataFactory.usage(resourceId2, stock2 - 1));
        RelocationOutputRequest request = RelocationDataFactory.buildSendMultiItem(
                scenario.memberStorageId(), scenario.crew().getId(), items);

        Response response = relocationFixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isBetween(200, 299);
        RelocationResponse sent = response.as(RelocationResponse.class);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
    }

    @Test(priority = 90)
    @TestCaseId("TC-CREW-REL-009")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_009)
    @Severity(SeverityLevel.NORMAL)
    public void testUnitToCrewRelocationHiddenFromAccountant() {
        RelocationResponse finished = relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        RelocationJournalQuery query = RelocationJournalQuery.sentHistoryUi(scenario.unit().getId())
                .toBuilder()
                .pageSize(100)
                .build();

        List<RelocationResponse> page = relocationFixture.getJournalPage(query, UserRole.ACCOUNTANT);
        assertThat(page.stream().map(RelocationResponse::getId))
                .as("UNIT→CREW не повинен бути видимий для accountant у журналі")
                .doesNotContain(finished.getId());
    }

    @Test(priority = 100)
    @TestCaseId("TC-CREW-REL-010")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_010)
    @Severity(SeverityLevel.CRITICAL)
    public void testRecipientCannotFinishCrewRelocation() {
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        Response denied = relocationFixture.resolveRaw(
                CREW_STOCK_READER, sent.getId(), scenario.crew().getId(), RelocationState.FINISHED);
        assertThat(denied.statusCode())
                .as("Прийняти на CREW може лише відправник")
                .isBetween(400, 499);

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
    }

    @Test(priority = 110)
    @TestCaseId("TC-CREW-REL-011")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_011)
    @Severity(SeverityLevel.CRITICAL)
    public void testSenderCanCancelCrewRelocationAndRestoreStock() {
        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before cancel send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew before cancel send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse returned = relocationFixture.resolve(
                UserRole.OWNER_1, sent.getId(), scenario.memberStorageId(), RelocationState.RETURNED);
        assertThat(returned.getState()).isEqualTo(RelocationState.RETURNED);

        ProductionStockAssertions.StockSnapshot afterSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after RETURNED");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), CREW_STOCK_READER,
                Set.of(resourceId), "crew after RETURNED");

        RelocationStockAssertions.assertUnchanged(
                beforeSender, afterSender, scenario.memberStorageId(), resourceId,
                "після скасування відправником stock відновлено");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId,
                "екіпаж без змін після скасування");
    }
}
