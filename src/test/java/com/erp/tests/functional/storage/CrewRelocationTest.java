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
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Видача ресурсів на екіпаж (relocation send → CREW, AUTO_FINISHED).
 */
@Slf4j
@Epic("Relocation")
@Feature("Crew Issuance")
@Story("Send to Crew")
public class CrewRelocationTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-rel-";
    private static final double ISSUE_AMOUNT = 15.0;

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
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-REL-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendToCrewAutoFinished() {
        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "before send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId), "crew before send");

        RelocationResponse relocation = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        assertThat(relocation.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        assertThat(relocation.getRecipient().getId()).isEqualTo(scenario.crew().getId());

        ProductionStockAssertions.StockSnapshot afterSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId), "after send");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId), "crew after send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSender, scenario.memberStorageId(), resourceId, ISSUE_AMOUNT,
                "видача на екіпаж");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId, ISSUE_AMOUNT,
                "зарахування на екіпаж");
    }

    @Test(priority = 20)
    @TestCaseId("TC-CREW-REL-002")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_002)
    @Severity(SeverityLevel.NORMAL)
    public void testCrewRelocationVisibleInJournal() {
        RelocationResponse relocation = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

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
                .contains(relocation.getId());
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

        var request = com.erp.data.factories.relocation.RelocationDataFactory.buildSendRequest(
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
    public void testMultiResourceSendToCrewAutoFinished() {
        ResourceResponse resource2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "b-");
        Long resourceId2 = resource2.getId();
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId2, 100.0);

        double amount1 = 10.0;
        double amount2 = 8.0;

        ProductionStockAssertions.StockSnapshot beforeSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "before multi send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "crew before multi send");

        List<ResourceUsageRequest> items = List.of(
                RelocationDataFactory.usage(resourceId, amount1),
                RelocationDataFactory.usage(resourceId2, amount2));
        RelocationOutputRequest request = RelocationDataFactory.buildSendMultiItem(
                scenario.memberStorageId(), scenario.crew().getId(), items);
        var sendResponse = relocationFixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(sendResponse.statusCode()).isBetween(200, 299);
        RelocationResponse relocation = sendResponse.as(RelocationResponse.class);

        assertThat(relocation.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot afterSender = RelocationStockAssertions.capture(
                apiExecutor, scenario.memberStorageId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "after multi send");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId, resourceId2), "crew after multi send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSender, scenario.memberStorageId(), resourceId, amount1,
                "multi send resource 1");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeSender, afterSender, scenario.memberStorageId(), resourceId2, amount2,
                "multi send resource 2");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId, amount1,
                "multi send crew resource 1");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId2, amount2,
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
        StorageResponse production = storageFixture.createChildStorage(
                scenario.unit().getId(),
                "crew-prod-",
                UnitType.PRODUCTION,
                StorageRelation.INTERNAL);
        relocationFixture.ensureStock(production.getId(), resourceId, 100.0);

        ProductionStockAssertions.StockSnapshot beforeProd = RelocationStockAssertions.capture(
                apiExecutor, production.getId(), UserRole.OWNER_1,
                Set.of(resourceId), "production before send");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId), "crew before production send");

        RelocationResponse relocation = relocationFixture.createSend(
                UserRole.OWNER_1,
                production.getId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        assertThat(relocation.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot afterProd = RelocationStockAssertions.capture(
                apiExecutor, production.getId(), UserRole.OWNER_1,
                Set.of(resourceId), "production after send");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, scenario.crew().getId(), UserRole.OWNER_1,
                Set.of(resourceId), "crew after production send");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeProd, afterProd, production.getId(), resourceId, ISSUE_AMOUNT,
                "PRODUCTION sender");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeCrew, afterCrew, scenario.crew().getId(), resourceId, ISSUE_AMOUNT,
                "crew after PRODUCTION send");
    }

    @Test(priority = 70)
    @TestCaseId("TC-CREW-REL-007")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_007)
    @Severity(SeverityLevel.NORMAL)
    public void testCrewRelocationVisibleInRecipientJournal() {
        RelocationResponse relocation = relocationFixture.createSend(
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

        List<RelocationResponse> page = relocationFixture.getJournalPage(query, UserRole.OWNER_1);
        assertThat(page.stream().map(RelocationResponse::getId)).contains(relocation.getId());
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

        var response = relocationFixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.as(RelocationResponse.class).getState()).isEqualTo(RelocationState.AUTO_FINISHED);
    }

    @Test(priority = 90)
    @TestCaseId("TC-CREW-REL-009")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_REL_009)
    @Severity(SeverityLevel.NORMAL)
    public void testUnitToCrewRelocationHiddenFromAccountant() {
        RelocationResponse relocation = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.unit().getId(),
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
                .doesNotContain(relocation.getId());
    }
}
