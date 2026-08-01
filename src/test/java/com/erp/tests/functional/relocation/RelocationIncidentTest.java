package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.enums.IncidentResourceOperation;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.IncidentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.models.response.RelocationIncidentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Relocation")
@Feature("Incident — надзвичайна подія")
public class RelocationIncidentTest extends BaseFunctionalTest {

    private IncidentFixture incidentFixture;
    private InventoryFixture inventoryFixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupIncidentTests() {
        incidentFixture = new IncidentFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        incidentFixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
    }

    @Test
    @TestCaseId("TC-INC-001")
    @Story("BC-INC-01 / BC-INC-02 / BC-INC-12: create → LOST + history on sender only")
    @Severity(SeverityLevel.BLOCKER)
    public void createIncidentMarksRelocationLostAndWritesHistory() {
        String marker = IncidentDataFactory.uniqueDescription();
        double amount = 3.0;

        double senderIncidentBefore = incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1);
        double recipientIncidentBefore = incidentSummaryAmount(owner2Storage, resourceId, UserRole.OWNER_2);

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount, marker);

        incidentFixture.createIncident(UserRole.OWNER_1, sent, marker);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(lost).as("Relocation appears in LOST journal").isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        assertThat(incident.getRelocationId()).isEqualTo(sent.getId());
        assertThat(incident.getDescription()).isEqualTo(marker);
        assertThat(incident.getResources()).isNotEmpty();
        assertThat(incident.getResources().getFirst().getOperation())
                .isEqualTo(IncidentResourceOperation.WRITE_OFF);
        assertThat(incident.getResources().getFirst().getStorageId()).isEqualTo(owner1Storage);
        assertThat(incident.getResources().getFirst().getResourceId()).isEqualTo(resourceId);

        Response senderHistory = inventoryFixture.getOperationHistoryToday(owner1Storage, UserRole.OWNER_1);
        assertThat(senderHistory.statusCode()).isEqualTo(200);
        assertThat(senderHistory.getBody().asString())
                .as("INCIDENT_WRITE_OFF must appear in sender operation history")
                .contains("INCIDENT_WRITE_OFF");
        assertThat(incidentSummaryAmount(senderHistory, resourceId) - senderIncidentBefore)
                .as("Sender totalIncidentResources must increase by write-off amount")
                .isCloseTo(amount, within(0.01));

        assertThat(incidentSummaryAmount(owner2Storage, resourceId, UserRole.OWNER_2))
                .as("Recipient history must not get INCIDENT_WRITE_OFF for this loss")
                .isCloseTo(recipientIncidentBefore, within(0.01));
    }

    @Test
    @TestCaseId("TC-INC-002")
    @Story("BC-INC-10: after incident recipient stock unchanged")
    @Severity(SeverityLevel.CRITICAL)
    public void createIncidentDoesNotCreditRecipient() {
        String marker = IncidentDataFactory.uniqueDescription();
        double amount = 2.0;
        Set<Long> tracked = Set.of(resourceId);

        ProductionStockAssertions.StockSnapshot beforeRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО incident");

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount, marker);
        incidentFixture.createIncident(UserRole.OWNER_1, sent, marker);

        ProductionStockAssertions.StockSnapshot afterRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ incident");
        RelocationStockAssertions.assertUnchanged(
                beforeRecipient, afterRecipient, owner2Storage, resourceId,
                "recipient must not receive cargo after incident");
    }

    @Test
    @TestCaseId("TC-INC-003")
    @Story("BC-INC-11: LOST relocation cannot be resolved")
    @Severity(SeverityLevel.CRITICAL)
    public void resolveLostRelocationIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 1.0, marker);
        incidentFixture.createIncident(UserRole.OWNER_1, sent, marker);

        RelocationUpdateRequest resolveRequest = RelocationUpdateRequest.builder()
                .state(RelocationState.FINISHED)
                .description("must fail on LOST")
                .build();
        Response response = apiExecutor.executeRelocationResolve(
                sent.getId(), owner2Storage, resolveRequest, UserRole.OWNER_2);

        assertThat(response.statusCode())
                .as("Resolve on LOST must be rejected")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @TestCaseId("TC-INC-004")
    @Story("BC-INC-20 / BC-INC-21: GET details + delete restores CREATED")
    @Severity(SeverityLevel.CRITICAL)
    public void deleteIncidentRestoresCreatedAndClearsHistory() {
        String marker = IncidentDataFactory.uniqueDescription();
        double amount = 1.0;

        double incidentBefore = incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1);

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount, marker);
        incidentFixture.createIncident(UserRole.OWNER_1, sent, marker);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        assertThat(incident.getDescription()).contains(marker);

        Response historyAfterCreate = inventoryFixture.getOperationHistoryToday(owner1Storage, UserRole.OWNER_1);
        assertThat(historyAfterCreate.statusCode()).isEqualTo(200);
        assertThat(historyAfterCreate.getBody().asString())
                .as("History must record INCIDENT_WRITE_OFF after create")
                .contains("INCIDENT_WRITE_OFF");
        assertThat(incidentSummaryAmount(historyAfterCreate, resourceId) - incidentBefore)
                .as("totalIncidentResources must increase by write-off amount")
                .isCloseTo(amount, within(0.01));

        incidentFixture.deleteIncident(UserRole.OWNER_1, sent.getId());

        Response getAfterDelete = apiExecutor.execute(
                ApiEndpointDefinition.INCIDENT_GET_BY_RELOCATION, UserRole.OWNER_1, null,
                String.valueOf(sent.getId()));
        assertThat(getAfterDelete.statusCode()).isEqualTo(404);

        RelocationResponse inTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(inTransit).as("After delete relocation returns to CREATED / in-transit").isNotNull();
        assertThat(inTransit.getState()).isEqualTo(RelocationState.CREATED);

        Response historyAfterDelete = inventoryFixture.getOperationHistoryToday(owner1Storage, UserRole.OWNER_1);
        assertThat(historyAfterDelete.statusCode()).isEqualTo(200);
        assertThat(incidentSummaryAmount(historyAfterDelete, resourceId))
                .as("Delete must clear this incident from totalIncidentResources")
                .isCloseTo(incidentBefore, within(0.01));
    }

    private double incidentSummaryAmount(long storageId, Long resourceId, UserRole role) {
        Response history = inventoryFixture.getOperationHistoryToday(storageId, role);
        assertThat(history.statusCode()).isEqualTo(200);
        return incidentSummaryAmount(history, resourceId);
    }

    private static double incidentSummaryAmount(Response history, Long resourceId) {
        var entries = history.jsonPath().getList("totalIncidentResources");
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < entries.size(); i++) {
            Long id = history.jsonPath().getLong("totalIncidentResources[" + i + "].resource.id");
            if (id != null && id.equals(resourceId)) {
                Number value = history.jsonPath().get("totalIncidentResources[" + i + "].amount");
                return value != null ? value.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }

    @Test
    @TestCaseId("TC-INC-005")
    @Story("BC-INC-30: incident only allowed on CREATED")
    @Severity(SeverityLevel.NORMAL)
    public void createIncidentOnFinishedIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 1.0, marker);
        incidentFixture.relocation().resolve(
                UserRole.OWNER_2, sent.getId(), owner2Storage, RelocationState.FINISHED);

        Response response = apiExecutor.executeIncidentCreate(
                IncidentDataFactory.buildFullCargoLoss(sent, marker), UserRole.OWNER_1);

        assertThat(response.statusCode())
                .as("Create incident on FINISHED must fail")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @TestCaseId("TC-INC-006")
    @Story("BC-INC-40: sender (alkatras/OWNER_1) creates incident → LOST")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Staging/dev: alkatras (OWNER_1) → bar (OWNER_2). Обидва мають incident::create.
            Відправник створює повну втрату на своєму send → HTTP 200, relocation → LOST
            (фактично автоматичне завершення переміщення як «Втрачено», resolve недоступний).
            """)
    public void ownerCanCreateIncident() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 1.0, marker);
        Response response = apiExecutor.executeIncidentCreate(
                IncidentDataFactory.buildFullCargoLoss(sent, marker), UserRole.OWNER_1);
        assertThat(response.statusCode())
                .as("Sender (OWNER_1/alkatras) can create incident")
                .isEqualTo(200);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(lost).as("After sender incident relocation is LOST").isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);
    }

    @Test
    @TestCaseId("TC-INC-012")
    @Story("BC-INC-41: recipient (bar/OWNER_2) creates incident → LOST")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Staging/dev: alkatras (OWNER_1) відправляє на bar (OWNER_2).
            Одержувач з incident::create і доступом до relocation (hasAccessToRelocation)
            створює повну втрату → HTTP 200, relocation → LOST
            (так само «автозавершення» переміщення через інцидент).
            """)
    public void recipientCanCreateIncidentAndTerminatesAsLost() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 1.0, marker);

        Response response = apiExecutor.executeIncidentCreate(
                IncidentDataFactory.buildFullCargoLoss(sent, marker), UserRole.OWNER_2);
        assertThat(response.statusCode())
                .as("Recipient (OWNER_2/bar) can create incident on incoming relocation")
                .isEqualTo(200);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_2, owner2Storage, marker);
        assertThat(lost).as("After recipient incident relocation is LOST").isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);

        RelocationResponse stillInTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(stillInTransit).as("Must not remain CREATED after incident").isNull();
    }

    @Test
    @TestCaseId("TC-INC-011")
    @Issue("CPMA-649")
    @Story("BC-INC-31: WRITE_OFF amount > sent rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Очікуваний контракт (як UI hasExceeding / «Перевищує кількість у переміщенні»):
            WRITE_OFF amount > sent → HTTP 4xx, relocation лишається CREATED, stock без змін.

            Відомий дефект CPMA-649: backend не валідує amount vs relocation item — зараз приймає 200.
            UI для повної втрати блокує поле (disabled на totalAmount).
            """)
    public void writeOffGreaterThanSentIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double writeOffAmount = sentAmount + 1.0;
        Set<Long> tracked = Set.of(resourceId);

        ProductionStockAssertions.StockSnapshot beforeRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО exceeding WRITE_OFF");

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);

        Response response = incidentFixture.createIncidentRaw(
                UserRole.OWNER_1,
                IncidentDataFactory.buildWriteOff(
                        sent.getId(), owner1Storage, resourceId, writeOffAmount, marker));

        assertThat(response.statusCode())
                .as("WRITE_OFF > sent must be rejected")
                .isGreaterThanOrEqualTo(400);

        RelocationResponse stillInTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(stillInTransit).as("Relocation stays CREATED after rejected WRITE_OFF").isNotNull();
        assertThat(stillInTransit.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(lost).as("Must not appear in LOST after rejected WRITE_OFF").isNull();

        ProductionStockAssertions.StockSnapshot afterRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ rejected WRITE_OFF");
        RelocationStockAssertions.assertUnchanged(
                beforeRecipient, afterRecipient, owner2Storage, resourceId,
                "rejected WRITE_OFF must not change recipient inventory");
    }
}
