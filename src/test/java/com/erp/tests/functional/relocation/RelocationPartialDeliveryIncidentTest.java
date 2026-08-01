package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.IncidentResourceOperation;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.IncidentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.IncidentResourceRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.models.response.RelocationIncidentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Надзвичайна подія — «часткова доставка вантажу» ({@code PARTIAL_DELIVERY}).
 * Клієнт шле лише delivered lines; backend (IncidentMapper) авто-додає WRITE_OFF remainder на sender.
 */
@Slf4j
@Epic("Relocation")
@Feature("Incident — часткова доставка вантажу")
public class RelocationPartialDeliveryIncidentTest extends BaseFunctionalTest {

    private IncidentFixture incidentFixture;
    private InventoryFixture inventoryFixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long resourceId;
    private Long resourceId2;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupPartialDeliveryIncidentTests() {
        incidentFixture = new IncidentFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        incidentFixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId2 = resources.size() > 1 ? resources.get(1).getId() : null;
        if (resourceId2 != null) {
            incidentFixture.relocation().ensureStock(owner1Storage, resourceId2, 200.0);
        }
    }

    @Test
    @TestCaseId("TC-INC-PD-001")
    @Story("BC-INC-PD-01: partial delivery → LOST + PARTIAL_DELIVERY + auto WRITE_OFF remainder")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Payload як tk-ui «Часткова доставка»: PARTIAL_DELIVERY на склад отримувача з delivered amount.
            Backend IncidentMapper додає WRITE_OFF remainder на sender (sent − delivered).
            Relocation → LOST.
            """)
    public void partialDeliveryMarksLostAndSplitsDeliveredVsWriteOff() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 10.0;
        double delivered = 4.0;
        double expectedWriteOff = sentAmount - delivered;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);

        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(lost).as("Relocation appears in LOST journal").isNotNull();
        assertThat(lost.getState()).isEqualTo(RelocationState.LOST);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        assertThat(incident.getDescription()).isEqualTo(marker);

        IncidentResourceRequest partial = findResource(
                incident.getResources(), IncidentResourceOperation.PARTIAL_DELIVERY, resourceId);
        assertThat(partial).as("PARTIAL_DELIVERY line present").isNotNull();
        assertThat(partial.getStorageId()).isEqualTo(owner2Storage);
        assertThat(partial.getAmount()).isCloseTo(BigDecimal.valueOf(delivered), within(new BigDecimal("0.01")));

        IncidentResourceRequest writeOff = findResource(
                incident.getResources(), IncidentResourceOperation.WRITE_OFF, resourceId);
        assertThat(writeOff).as("Auto WRITE_OFF remainder on sender").isNotNull();
        assertThat(writeOff.getStorageId()).isEqualTo(owner1Storage);
        assertThat(writeOff.getAmount())
                .isCloseTo(BigDecimal.valueOf(expectedWriteOff), within(new BigDecimal("0.01")));
    }

    @Test
    @TestCaseId("TC-INC-PD-002")
    @Story("BC-INC-PD-02: history — WRITE_OFF on sender, ADDED on delivery storage")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після часткової доставки:
            — sender: INCIDENT_WRITE_OFF += remainder;
            — delivery storage (отримувач): totalAddedResources / ADDED += delivered.
            """)
    public void partialDeliveryWritesHistoryOnSenderAndDeliveryStorage() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 8.0;
        double delivered = 3.0;
        double expectedWriteOff = sentAmount - delivered;

        double senderIncidentBefore = incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1);
        double recipientAddedBefore = addedSummaryAmount(owner2Storage, resourceId, UserRole.OWNER_2);

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        Response senderHistory = inventoryFixture.getOperationHistoryToday(owner1Storage, UserRole.OWNER_1);
        assertThat(senderHistory.statusCode()).isEqualTo(200);
        assertThat(senderHistory.getBody().asString())
                .as("INCIDENT_WRITE_OFF on sender for remainder")
                .contains("INCIDENT_WRITE_OFF");
        assertThat(incidentSummaryAmount(senderHistory, resourceId) - senderIncidentBefore)
                .as("Sender totalIncidentResources += remainder")
                .isCloseTo(expectedWriteOff, within(0.01));

        Response recipientHistory = inventoryFixture.getOperationHistoryToday(owner2Storage, UserRole.OWNER_2);
        assertThat(recipientHistory.statusCode()).isEqualTo(200);
        assertThat(recipientHistory.getBody().asString())
                .as("PARTIAL_DELIVERY is recorded as ADDED on delivery storage")
                .contains("ADDED");
        assertThat(addedSummaryAmount(recipientHistory, resourceId) - recipientAddedBefore)
                .as("Recipient totalAddedResources += delivered")
                .isCloseTo(delivered, within(0.01));
    }

    @Test
    @TestCaseId("TC-INC-PD-003")
    @Issue("CPMA-650")
    @Story("BC-INC-PD-03: recipient inventory += delivered after partial delivery")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Очікуваний контракт: PARTIAL_DELIVERY зараховує delivered на StorageItem складу доставки
            (аналог StorageItemService.put при FINISHED).

            Відомий дефект CPMA-650: IncidentFacade пише лише історію (ADDED), без мутації inventory —
            залишок на отримувачі не змінюється. Тест червоний до фіксу в tk.
            """)
    public void partialDeliveryCreditsRecipientInventoryStock() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double delivered = 2.0;
        Set<Long> tracked = Set.of(resourceId);

        ProductionStockAssertions.StockSnapshot beforeRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО partial delivery");

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        ProductionStockAssertions.StockSnapshot afterRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ partial delivery");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeRecipient, afterRecipient, owner2Storage, resourceId, delivered,
                "PARTIAL_DELIVERY must credit delivery storage inventory");
    }

    @Test
    @TestCaseId("TC-INC-PD-004")
    @Story("BC-INC-PD-04: delete after partial delivery restores CREATED + stock/history rollback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            DELETE інциденту після часткової доставки: GET 404, relocation знову CREATED,
            totalIncidentResources на sender повертається (INCIDENT_WRITE_OFF cleanup),
            recipient stock і ADDED-історія відкочуються до стану до PD.

            Відомі дефекти (tk):
            1) create не кредитує StorageItem → rollback stock теж відсутній;
            2) onIncidentDeleted чистить лише INCIDENT_WRITE_OFF, не ADDED.
            Тест червоний до фіксу в tk.
            """)
    public void deletePartialDeliveryIncidentRestoresCreated() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 6.0;
        double delivered = 2.0;
        double expectedWriteOff = sentAmount - delivered;
        Set<Long> tracked = Set.of(resourceId);

        double incidentBefore = incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1);
        double addedBefore = addedSummaryAmount(owner2Storage, resourceId, UserRole.OWNER_2);
        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО PD (baseline for delete)");

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        assertThat(incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1) - incidentBefore)
                .isCloseTo(expectedWriteOff, within(0.01));

        incidentFixture.deleteIncident(UserRole.OWNER_1, sent.getId());

        Response getAfterDelete = apiExecutor.execute(
                ApiEndpointDefinition.INCIDENT_GET_BY_RELOCATION, UserRole.OWNER_1, null,
                String.valueOf(sent.getId()));
        assertThat(getAfterDelete.statusCode()).isEqualTo(404);

        RelocationResponse inTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(inTransit).as("After delete relocation returns to CREATED").isNotNull();
        assertThat(inTransit.getState()).isEqualTo(RelocationState.CREATED);

        assertThat(incidentSummaryAmount(owner1Storage, resourceId, UserRole.OWNER_1))
                .as("Delete clears INCIDENT_WRITE_OFF from totalIncidentResources")
                .isCloseTo(incidentBefore, within(0.01));

        assertThat(addedSummaryAmount(owner2Storage, resourceId, UserRole.OWNER_2))
                .as("Delete must clear ADDED history from PARTIAL_DELIVERY")
                .isCloseTo(addedBefore, within(0.01));

        ProductionStockAssertions.StockSnapshot stockAfterDelete = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ delete PD");
        RelocationStockAssertions.assertUnchanged(
                stockBefore, stockAfterDelete, owner2Storage, resourceId,
                "delete must roll back recipient inventory credit from PARTIAL_DELIVERY");
    }

    @Test
    @TestCaseId("TC-INC-PD-005")
    @Story("BC-INC-PD-05: delivered == full amount → no auto WRITE_OFF")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Якщо delivered = amount у переміщенні, IncidentMapper не додає WRITE_OFF remainder.
            У GET resources лише PARTIAL_DELIVERY.
            """)
    public void fullAmountPartialDeliveryHasNoWriteOffRemainder() {
        String marker = IncidentDataFactory.uniqueDescription();
        double amount = 3.0;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, amount, marker);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        assertThat(incident.getResources())
                .filteredOn(r -> r.getOperation() == IncidentResourceOperation.WRITE_OFF)
                .as("No WRITE_OFF when everything was delivered")
                .isEmpty();
        assertThat(findResource(
                incident.getResources(), IncidentResourceOperation.PARTIAL_DELIVERY, resourceId))
                .isNotNull();
    }

    @Test
    @TestCaseId("TC-INC-PD-006")
    @Story("BC-INC-PD-06: LOST after partial delivery cannot be resolved")
    @Severity(SeverityLevel.CRITICAL)
    public void resolveAfterPartialDeliveryIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 4.0, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, 1.0, marker);

        RelocationUpdateRequest resolveRequest = RelocationUpdateRequest.builder()
                .state(RelocationState.FINISHED)
                .description("must fail on LOST after partial delivery")
                .build();
        Response response = apiExecutor.executeRelocationResolve(
                sent.getId(), owner2Storage, resolveRequest, UserRole.OWNER_2);

        assertThat(response.statusCode())
                .as("Resolve on LOST must be rejected")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @TestCaseId("TC-INC-PD-007")
    @Issue("CPMA-650")
    @Story("BC-INC-PD-07: multi-item partial delivery splits per resource")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Multi-item relocation: client sends PARTIAL_DELIVERY map per resource;
            backend auto WRITE_OFF remainder per resource; delivery storage stock += delivered
            for each resource.

            Відомий дефект CPMA-650: stock credit відсутній (як TC-INC-PD-003).
            """)
    public void multiItemPartialDeliverySplitsPerResource() {
        assertThat(resourceId2).as("Need 2 shared resources for multi-item PD").isNotNull();

        String marker = IncidentDataFactory.uniqueDescription();
        double sent1 = 20.0;
        double sent2 = 30.0;
        double delivered1 = 10.0;
        double delivered2 = 17.0;
        Set<Long> tracked = Set.of(resourceId, resourceId2);

        ProductionStockAssertions.StockSnapshot beforeRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО multi-item PD");

        RelocationResponse sent = incidentFixture.relocation().createSendMultiItem(
                UserRole.OWNER_1,
                owner1Storage,
                owner2Storage,
                List.of(
                        RelocationDataFactory.usage(resourceId, sent1),
                        RelocationDataFactory.usage(resourceId2, sent2)),
                marker);

        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1,
                sent,
                owner2Storage,
                Map.of(resourceId, delivered1, resourceId2, delivered2),
                marker);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());

        IncidentResourceRequest pd1 = findResource(
                incident.getResources(), IncidentResourceOperation.PARTIAL_DELIVERY, resourceId);
        IncidentResourceRequest pd2 = findResource(
                incident.getResources(), IncidentResourceOperation.PARTIAL_DELIVERY, resourceId2);
        assertThat(pd1.getAmount()).isCloseTo(BigDecimal.valueOf(delivered1), within(new BigDecimal("0.01")));
        assertThat(pd2.getAmount()).isCloseTo(BigDecimal.valueOf(delivered2), within(new BigDecimal("0.01")));

        IncidentResourceRequest wo1 = findResource(
                incident.getResources(), IncidentResourceOperation.WRITE_OFF, resourceId);
        IncidentResourceRequest wo2 = findResource(
                incident.getResources(), IncidentResourceOperation.WRITE_OFF, resourceId2);
        assertThat(wo1.getAmount())
                .isCloseTo(BigDecimal.valueOf(sent1 - delivered1), within(new BigDecimal("0.01")));
        assertThat(wo2.getAmount())
                .isCloseTo(BigDecimal.valueOf(sent2 - delivered2), within(new BigDecimal("0.01")));

        ProductionStockAssertions.StockSnapshot afterRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ multi-item PD");
        RelocationStockAssertions.assertStockDelta(
                beforeRecipient, afterRecipient, owner2Storage,
                Map.of(resourceId, delivered1, resourceId2, delivered2),
                "multi-item PARTIAL_DELIVERY must credit each resource");
    }

    @Test
    @TestCaseId("TC-INC-PD-008")
    @Issue("CPMA-650")
    @Story("BC-INC-PD-08: delivery storage = sender")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI дозволяє обрати sender як склад доставки.
            PARTIAL_DELIVERY на sender; stock sender += delivered; auto WRITE_OFF remainder на sender.

            Відомий дефект CPMA-650: stock credit відсутній (як TC-INC-PD-003).
            Note: after send, sender already debited full sent amount; expected net after PD
            credit is −(sent − delivered) vs pre-send, i.e. +delivered vs post-send snapshot.
            """)
    public void partialDeliveryToSenderStorageCreditsSender() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 7.0;
        double delivered = 3.0;
        double expectedWriteOff = sentAmount - delivered;
        Set<Long> tracked = Set.of(resourceId);

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);

        ProductionStockAssertions.StockSnapshot beforePd = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ send / ДО PD to sender");

        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner1Storage, resourceId, delivered, marker);

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        IncidentResourceRequest partial = findResource(
                incident.getResources(), IncidentResourceOperation.PARTIAL_DELIVERY, resourceId);
        assertThat(partial.getStorageId()).isEqualTo(owner1Storage);
        assertThat(partial.getAmount()).isCloseTo(BigDecimal.valueOf(delivered), within(new BigDecimal("0.01")));

        IncidentResourceRequest writeOff = findResource(
                incident.getResources(), IncidentResourceOperation.WRITE_OFF, resourceId);
        assertThat(writeOff.getStorageId()).isEqualTo(owner1Storage);
        assertThat(writeOff.getAmount())
                .isCloseTo(BigDecimal.valueOf(expectedWriteOff), within(new BigDecimal("0.01")));

        ProductionStockAssertions.StockSnapshot afterPd = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ PD to sender");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforePd, afterPd, owner1Storage, resourceId, delivered,
                "PARTIAL_DELIVERY to sender must credit sender inventory by delivered");
    }

    @Test
    @TestCaseId("TC-INC-PD-009")
    @Issue("CPMA-651")
    @Story("BC-INC-PD-09: delivered > sent rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Очікуваний контракт (як UI hasExceeding / TC-UI-INC-PD-004): delivered > sent → HTTP 4xx,
            relocation лишається CREATED, inventory без змін.

            Відомий дефект CPMA-651: backend не валідує amount vs relocation item — зараз приймає.
            """)
    public void deliveredGreaterThanSentIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double delivered = 8.0;
        Set<Long> tracked = Set.of(resourceId);

        ProductionStockAssertions.StockSnapshot beforeRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ДО exceeding PD");

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);

        Response response = incidentFixture.createPartialDeliveryIncidentRaw(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        assertThat(response.statusCode())
                .as("delivered > sent must be rejected")
                .isGreaterThanOrEqualTo(400);

        RelocationResponse stillInTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(stillInTransit).as("Relocation stays CREATED after rejected PD").isNotNull();
        assertThat(stillInTransit.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot afterRecipient = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "ПІСЛЯ rejected exceeding PD");
        RelocationStockAssertions.assertUnchanged(
                beforeRecipient, afterRecipient, owner2Storage, resourceId,
                "rejected PD must not change recipient inventory");
    }

    @Test
    @TestCaseId("TC-INC-PD-010")
    @Issue("CPMA-651")
    @Story("BC-INC-PD-10: negative delivered amount rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Очікуваний контракт (як UI TC-UI-INC-PD-005): delivered < 0 → HTTP 4xx, relocation не стає LOST.

            Відомий дефект CPMA-651: backend не валідує negative amount.
            """)
    public void negativeDeliveredAmountIsRejected() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 4.0;
        double delivered = -1.0;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sentAmount, marker);

        Response response = incidentFixture.createPartialDeliveryIncidentRaw(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        assertThat(response.statusCode())
                .as("negative delivered must be rejected")
                .isGreaterThanOrEqualTo(400);

        RelocationResponse stillInTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(stillInTransit).as("Relocation stays CREATED after negative PD").isNotNull();
        assertThat(stillInTransit.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse lost = incidentFixture.findLostByDescription(
                UserRole.OWNER_1, owner1Storage, marker);
        assertThat(lost).as("Must not appear in LOST after rejected negative PD").isNull();
    }

    private static IncidentResourceRequest findResource(List<IncidentResourceRequest> resources,
                                                        IncidentResourceOperation operation,
                                                        Long resourceId) {
        return resources.stream()
                .filter(r -> r.getOperation() == operation)
                .filter(r -> resourceId.equals(r.getResourceId()))
                .findFirst()
                .orElse(null);
    }

    private double incidentSummaryAmount(long storageId, Long resourceId, UserRole role) {
        Response history = inventoryFixture.getOperationHistoryToday(storageId, role);
        assertThat(history.statusCode()).isEqualTo(200);
        return incidentSummaryAmount(history, resourceId);
    }

    private static double incidentSummaryAmount(Response history, Long resourceId) {
        return summaryAmount(history, "totalIncidentResources", resourceId);
    }

    private double addedSummaryAmount(long storageId, Long resourceId, UserRole role) {
        Response history = inventoryFixture.getOperationHistoryToday(storageId, role);
        assertThat(history.statusCode()).isEqualTo(200);
        return addedSummaryAmount(history, resourceId);
    }

    private static double addedSummaryAmount(Response history, Long resourceId) {
        return summaryAmount(history, "totalAddedResources", resourceId);
    }

    private static double summaryAmount(Response history, String field, Long resourceId) {
        var entries = history.jsonPath().getList(field);
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < entries.size(); i++) {
            Long id = history.jsonPath().getLong(field + "[" + i + "].resource.id");
            if (id != null && id.equals(resourceId)) {
                Number value = history.jsonPath().get(field + "[" + i + "].amount");
                return value != null ? value.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }
}
