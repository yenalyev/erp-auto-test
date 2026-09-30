package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.enums.DefectType;
import com.erp.fixtures.DefectFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationBatchAssertions;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** External receipt edits after a defect explicitly names the receipt batch UUID. */
@Epic("Relocations")
@Feature("Edit external receipt after explicit batch use")
public class RelocationUsedBatchEditTest extends BaseFunctionalTest {

    private DefectFixture fixture;
    private Long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setUpUsedBatchEdits() {
        fixture = new DefectFixture(testContext, apiExecutor);
        fixture.prepareIsolatedContext(getPlaywrightSessionProvider());
        storageId = fixture.getStorageId();
    }

    @AfterClass(alwaysRun = true)
    public void cleanUpUsedBatchEdits() {
        if (fixture != null) {
            fixture.cleanupIsolatedContext();
        }
    }

    private record Scenario(Long resourceId, String batchNumber, UUID batchUuid,
                            RelocationResponse receipt, List<DefectResponse> defects, double used) {}

    private Scenario createScenario(double... defectAmounts) {
        double used = 0.0;
        for (double amount : defectAmounts) {
            assertThat(amount).isPositive();
            used += amount;
        }
        assertThat(used).isLessThanOrEqualTo(10.0);
        Long resourceId = fixture.createFreshResource();
        String batchNumber = "rel-used-" + UUID.randomUUID();
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(0.0, within(0.01));
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, batchNumber, true);
        UUID batchUuid = ProductionStockAssertions.requireBatchUuid(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId, batchNumber, true);

        List<DefectResponse> persistedDefects = new java.util.ArrayList<>();
        for (double amount : defectAmounts) {
            DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                    DefectDataFactory.buildStorageExplicitBatchesDefect(storageId, resourceId, amount,
                            List.of(DefectDataFactory.batch(batchUuid, batchNumber, true, amount))));
            // Every defect must already be persisted before any receipt update is attempted.
            DefectResponse persisted = fixture.getById(UserRole.OWNER_1, defect.getId());
            assertThat(persisted.getAmount().doubleValue()).isCloseTo(amount, within(0.01));
            assertThat(persisted.getDefectBatches()).extracting(b -> b.getBatchUuid())
                    .contains(batchUuid);
            persistedDefects.add(persisted);
        }
        assertStock(resourceId, batchNumber, 10.0 - used);
        return new Scenario(resourceId, batchNumber, batchUuid, receipt, persistedDefects, used);
    }

    private RelocationInputEditRequest edit(Scenario scenario, double amount, String description) {
        return RelocationInputEditRequest.builder()
                .date(scenario.receipt().getDate())
                .description(description)
                .invoiceNumber(scenario.receipt().getInvoiceNumber())
                .items(List.of(RelocationDataFactory.usageForExternalBatch(
                        scenario.resourceId(), amount, scenario.batchNumber(), true)))
                .build();
    }

    private void assertStock(Long resourceId, String batchNumber, double expected) {
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(expected, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, batchNumber, true, "receipt batch stock").amount())
                .isCloseTo(expected, within(0.01));
    }

    private void assertDefectUnchanged(Scenario scenario) {
        double total = 0.0;
        for (DefectResponse defect : scenario.defects()) {
            DefectResponse persisted = fixture.getById(UserRole.OWNER_1, defect.getId());
            assertThat(persisted.getAmount()).isEqualByComparingTo(defect.getAmount());
            assertThat(persisted.getDefectBatches()).extracting(b -> b.getBatchUuid())
                    .contains(scenario.batchUuid());
            total += persisted.getAmount().doubleValue();
        }
        assertThat(total).isCloseTo(scenario.used(), within(0.01));
    }

    private void assertReceipt(Scenario scenario, double expectedAmount, String expectedDescription) {
        RelocationResponse persisted = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.OWNER_1).stream()
                .filter(item -> scenario.receipt().getId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Receipt missing from journal: "
                        + scenario.receipt().getId()));
        assertThat(persisted.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(expectedAmount, within(0.01));
        assertThat(persisted.getDescription()).isEqualTo(expectedDescription);
    }

    @Test
    @TestCaseId("TC-REL-USED-001")
    @Story("Partly used batch: reject reduction below explicitly consumed amount")
    public void rejectReductionBelowPartialDefect() {
        Scenario scenario = createScenario(4.0);
        Response response = apiExecutor.executeRelocationUpdateReceive(
                scenario.receipt().getId(), storageId,
                edit(scenario, 3.0, "must be rejected"), UserRole.ADMIN);
        assertThat(response.statusCode()).as("body=%s", response.asString()).isBetween(400, 499);
        assertReceipt(scenario, 10.0, scenario.receipt().getDescription());
        assertStock(scenario.resourceId(), scenario.batchNumber(), 6.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-002")
    @Story("Fully used batch: reject any reduction of receipt amount")
    public void rejectReductionBelowFullDefect() {
        Scenario scenario = createScenario(10.0);
        Response response = apiExecutor.executeRelocationUpdateReceive(
                scenario.receipt().getId(), storageId,
                edit(scenario, 9.0, "must be rejected"), UserRole.ADMIN);
        assertThat(response.statusCode()).as("body=%s", response.asString()).isBetween(400, 499);
        assertReceipt(scenario, 10.0, scenario.receipt().getDescription());
        assertStock(scenario.resourceId(), scenario.batchNumber(), 0.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-003")
    @Story("Partly used batch: receipt amount may equal the consumed amount")
    public void allowReductionToConsumedBoundary() {
        Scenario scenario = createScenario(4.0);
        RelocationResponse updated = fixture.getRelocationFixture().editExternalReceive(
                UserRole.ADMIN, scenario.receipt().getId(), storageId,
                edit(scenario, 4.0, "boundary amount"));
        assertThat(updated.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(4.0, within(0.01));
        assertReceipt(scenario, 4.0, "boundary amount");
        assertStock(scenario.resourceId(), scenario.batchNumber(), 0.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-004")
    @Story("Partly used batch: increase receipt without changing the defect")
    public void allowIncreaseAfterPartialDefect() {
        Scenario scenario = createScenario(4.0);
        RelocationResponse updated = fixture.getRelocationFixture().editExternalReceive(
                UserRole.ADMIN, scenario.receipt().getId(), storageId,
                edit(scenario, 12.0, "increased amount"));
        assertThat(updated.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(12.0, within(0.01));
        assertReceipt(scenario, 12.0, "increased amount");
        assertStock(scenario.resourceId(), scenario.batchNumber(), 8.0);
        assertThat(ProductionStockAssertions.requireBatchUuid(apiExecutor, storageId,
                UserRole.OWNER_1, scenario.resourceId(), scenario.batchNumber(), true))
                .isEqualTo(scenario.batchUuid());
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-005")
    @Story("Fully used batch: edit receipt description without touching consumption")
    public void allowMetadataEditAfterFullDefect() {
        Scenario scenario = createScenario(10.0);
        RelocationResponse updated = fixture.getRelocationFixture().editExternalReceive(
                UserRole.ADMIN, scenario.receipt().getId(), storageId,
                edit(scenario, 10.0, "metadata after explicit defect"));
        assertThat(updated.getDescription()).isEqualTo("metadata after explicit defect");
        assertReceipt(scenario, 10.0, "metadata after explicit defect");
        assertStock(scenario.resourceId(), scenario.batchNumber(), 0.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-006")
    @Story("Two explicit defects: reject receipt amount below their total")
    public void rejectReductionBelowTotalOfTwoDefects() {
        Scenario scenario = createScenario(2.0, 3.0);
        Response response = apiExecutor.executeRelocationUpdateReceive(
                scenario.receipt().getId(), storageId,
                edit(scenario, 4.0, "below total defect"), UserRole.ADMIN);
        assertThat(response.statusCode()).as("body=%s", response.asString()).isBetween(400, 499);
        assertReceipt(scenario, 10.0, scenario.receipt().getDescription());
        assertStock(scenario.resourceId(), scenario.batchNumber(), 5.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-007")
    @Story("Two explicit defects: receipt amount may equal their total")
    public void allowReductionToTotalOfTwoDefects() {
        Scenario scenario = createScenario(2.0, 3.0);
        RelocationResponse updated = fixture.getRelocationFixture().editExternalReceive(
                UserRole.ADMIN, scenario.receipt().getId(), storageId,
                edit(scenario, 5.0, "equal to total defect"));
        assertThat(updated.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(5.0, within(0.01));
        assertReceipt(scenario, 5.0, "equal to total defect");
        assertStock(scenario.resourceId(), scenario.batchNumber(), 0.0);
        assertDefectUnchanged(scenario);
    }

    @Test
    @TestCaseId("TC-REL-USED-008")
    @Story("Supplier receipt defect 8: reject editing the same receipt from 10 to 4")
    public void rejectSupplierReceiptReductionBelowLinkedDefect() {
        Long resourceId = fixture.createFreshResource();
        String batchNumber = "supplier-defect-" + UUID.randomUUID();
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, batchNumber);
        DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(
                        storageId, resourceId, receipt.getId(), 8.0, LocalDate.now()));
        DefectResponse persistedBeforeEdit = fixture.getById(UserRole.OWNER_1, defect.getId());
        assertThat(persistedBeforeEdit.getType()).isEqualTo(DefectType.RELOCATION);
        assertThat(persistedBeforeEdit.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(persistedBeforeEdit.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(2.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, batchNumber, false, "supplier defect before receipt edit").amount())
                .isCloseTo(2.0, within(0.01));

        RelocationInputEditRequest editedReceipt = RelocationInputEditRequest.builder()
                .date(receipt.getDate())
                .description("must reject supplier defect 8 against receipt 4")
                .invoiceNumber(receipt.getInvoiceNumber())
                .items(List.of(RelocationDataFactory.usageForExternalBatch(
                        resourceId, 4.0, batchNumber, false)))
                .build();
        Response response = apiExecutor.executeRelocationUpdateReceive(
                receipt.getId(), storageId, editedReceipt, UserRole.ADMIN);

        RelocationResponse persistedReceipt = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.OWNER_1).stream()
                .filter(item -> receipt.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Receipt missing from journal: " + receipt.getId()));
        DefectResponse persistedAfterEdit = fixture.getById(UserRole.OWNER_1, defect.getId());
        double stockAfterEdit = fixture.resourceStock(resourceId);
        double batchAfterEdit = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, batchNumber, false,
                "supplier defect after receipt edit").amount();

        assertThat(response.statusCode())
                .as("supplier defect 8, requested receipt 4; persisted receipt=%s, defect=%s, stock=%s, batch=%s; body=%s",
                        persistedReceipt.getItems().getFirst().getAmount(),
                        persistedAfterEdit.getAmount(), stockAfterEdit, batchAfterEdit, response.asString())
                .isBetween(400, 499);
        assertThat(persistedReceipt.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(10.0, within(0.01));
        assertThat(persistedAfterEdit.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(persistedAfterEdit.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(stockAfterEdit).isCloseTo(2.0, within(0.01));
        assertThat(batchAfterEdit).isCloseTo(2.0, within(0.01));
    }

    @Test
    @TestCaseId("TC-REL-USED-009")
    @Story("Other batch stock must not permit reducing a supplier receipt below its linked defect")
    public void rejectSupplierReceiptReductionWithAnotherBatchInStock() {
        Long resourceId = fixture.createFreshResource();
        String receiptBatch = "supplier-defect-source-" + UUID.randomUUID();
        String otherBatch = "supplier-defect-other-" + UUID.randomUUID();
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, receiptBatch);
        fixture.createExternalReceipt(resourceId, 10.0, otherBatch);
        DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(
                        storageId, resourceId, receipt.getId(), 8.0, LocalDate.now()));
        DefectResponse savedDefect = fixture.getById(UserRole.OWNER_1, defect.getId());
        assertThat(savedDefect.getType()).isEqualTo(DefectType.RELOCATION);
        assertThat(savedDefect.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(savedDefect.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(12.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, receiptBatch, false, "linked batch before edit").amount())
                .isCloseTo(2.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, otherBatch, false, "unrelated batch before edit").amount())
                .isCloseTo(10.0, within(0.01));

        RelocationInputEditRequest edit = RelocationInputEditRequest.builder()
                .date(receipt.getDate())
                .description("must not use unrelated batch to cover linked defect")
                .invoiceNumber(receipt.getInvoiceNumber())
                .items(List.of(RelocationDataFactory.usageForExternalBatch(
                        resourceId, 4.0, receiptBatch, false)))
                .build();
        Response response = apiExecutor.executeRelocationUpdateReceive(
                receipt.getId(), storageId, edit, UserRole.ADMIN);

        RelocationResponse savedReceipt = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.ADMIN).stream()
                .filter(item -> receipt.getId().equals(item.getId()))
                .findFirst().orElseThrow();
        DefectResponse defectAfterEdit = fixture.getById(UserRole.OWNER_1, defect.getId());
        double stock = fixture.resourceStock(resourceId);
        double linkedBatchStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, receiptBatch, false, "linked batch after edit").amount();
        double otherBatchStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, otherBatch, false, "unrelated batch after edit").amount();

        assertThat(response.statusCode())
                .as("linked receipt=%s, defect=%s, total stock=%s, linked batch=%s, other batch=%s; body=%s",
                        savedReceipt.getItems().getFirst().getAmount(), defectAfterEdit.getAmount(),
                        stock, linkedBatchStock, otherBatchStock, response.asString())
                .isBetween(400, 499);
        assertThat(savedReceipt.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(10.0, within(0.01));
        assertThat(defectAfterEdit.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(defectAfterEdit.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(stock).isCloseTo(12.0, within(0.01));
        assertThat(linkedBatchStock).isCloseTo(2.0, within(0.01));
        assertThat(otherBatchStock).isCloseTo(10.0, within(0.01));
    }

    @Test
    @TestCaseId("TC-REL-USED-010")
    @Story("Other batch stock must not permit reducing a receipt below its explicit STORAGE defect")
    public void rejectExplicitStorageDefectReductionWithAnotherBatchInStock() {
        Long resourceId = fixture.createFreshResource();
        String receiptBatch = "storage-defect-source-" + UUID.randomUUID();
        String otherBatch = "storage-defect-other-" + UUID.randomUUID();
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, receiptBatch, true);
        fixture.createExternalReceipt(resourceId, 10.0, otherBatch, true);
        UUID batchUuid = ProductionStockAssertions.requireBatchUuid(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, receiptBatch, true);
        DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageExplicitBatchesDefect(storageId, resourceId, 8.0,
                        List.of(DefectDataFactory.batch(batchUuid, receiptBatch, true, 8.0))));
        DefectResponse savedDefect = fixture.getById(UserRole.OWNER_1, defect.getId());
        assertThat(savedDefect.getType()).isEqualTo(DefectType.STORAGE);
        assertThat(savedDefect.getDefectBatches()).extracting(b -> b.getBatchUuid()).contains(batchUuid);
        assertThat(savedDefect.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(12.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, receiptBatch, true, "explicitly used batch before edit").amount())
                .isCloseTo(2.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId, UserRole.OWNER_1,
                resourceId, otherBatch, true, "other batch before edit").amount())
                .isCloseTo(10.0, within(0.01));

        RelocationInputEditRequest edit = RelocationInputEditRequest.builder()
                .date(receipt.getDate())
                .description("must not use unrelated batch to cover explicit defect")
                .invoiceNumber(receipt.getInvoiceNumber())
                .items(List.of(RelocationDataFactory.usageForExternalBatch(
                        resourceId, 4.0, receiptBatch, true)))
                .build();
        Response response = apiExecutor.executeRelocationUpdateReceive(
                receipt.getId(), storageId, edit, UserRole.ADMIN);
        RelocationResponse savedReceipt = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.ADMIN).stream()
                .filter(item -> receipt.getId().equals(item.getId()))
                .findFirst().orElseThrow();
        DefectResponse defectAfterEdit = fixture.getById(UserRole.OWNER_1, defect.getId());
        double stock = fixture.resourceStock(resourceId);
        double linkedStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, receiptBatch, true, "explicitly used batch after edit").amount();
        double otherStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, otherBatch, true, "other batch after edit").amount();
        assertThat(response.statusCode())
                .as("explicit receipt=%s, defect=%s, total stock=%s, linked batch=%s, other batch=%s; body=%s",
                        savedReceipt.getItems().getFirst().getAmount(), defectAfterEdit.getAmount(),
                        stock, linkedStock, otherStock, response.asString())
                .isBetween(400, 499);
        assertThat(savedReceipt.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(10.0, within(0.01));
        assertThat(defectAfterEdit.getDefectBatches()).extracting(b -> b.getBatchUuid())
                .contains(batchUuid);
        assertThat(defectAfterEdit.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(stock).isCloseTo(12.0, within(0.01));
        assertThat(linkedStock).isCloseTo(2.0, within(0.01));
        assertThat(otherStock).isCloseTo(10.0, within(0.01));
    }
}
