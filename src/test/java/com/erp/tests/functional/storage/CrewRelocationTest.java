package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
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
}
