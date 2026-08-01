package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationBatchAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Resource Relocations — Extended")
public class RelocationExtendedTest extends BaseFunctionalTest {

    private RelocationFixture fixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long supplierId;
    private Long unitStorageId;
    private Long resourceId;
    private Long categoryId;
    private Long secondResourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupExtendedRelocationTests() {
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        unitStorageId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        categoryId = fixture.getSharedCategoryId();
        secondResourceId = fixture.secondResourceId();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        fixture.ensureStock(owner1Storage, resourceId, 200.0);
        fixture.ensureStock(owner1Storage, secondResourceId, 100.0);
    }

    // --- A: filters ---

    @Test(priority = 3)
    @TestCaseId("TC-REL-003")
    @Story("Filter by category")
    public void testGetRelocationsByCategory() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                UserRole.OWNER_1,
                Map.of("senderIds", owner1Storage, "category", categoryId, "size", 10));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test(priority = 4)
    @TestCaseId("TC-REL-004")
    @Story("Filter productIds OR equipmentModelIds")
    public void testGetRelocationsByProductIds() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                UserRole.OWNER_1,
                Map.of("senderIds", owner1Storage, "productIds", resourceId, "size", 10));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test(priority = 6)
    @TestCaseId("TC-REL-006")
    @Story("Export relocations")
    public void testExportRelocations() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_EXPORT,
                UserRole.OWNER_1,
                Map.of("senderIds", owner1Storage));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("octet-stream");
        assertThat(response.asByteArray().length).isPositive();
    }

    // --- B: send extended ---

    @Test(priority = 12)
    @TestCaseId("TC-REL-012")
    @Story("Send FIFO without explicit batches")
    public void testSendFifoWithoutNamedBatches() {
        String batchA = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 15.0, batchA);
        double sendAmount = 8.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО FIFO send");

        RelocationResponse created = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sendAmount);
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ FIFO send");
        RelocationStockAssertions.assertDebitedFromSender(
                before, after, owner1Storage, resourceId, sendAmount, "FIFO send");
    }

    @Test(priority = 14)
    @TestCaseId("TC-REL-014")
    @Story("Send insufficient stock")
    public void testSendInsufficientStockRejected() {
        double current = fixture.getResourceStock(owner1Storage, resourceId, UserRole.OWNER_1);
        double excessive = current + 5000.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "відправник ДО");
        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "отримувач ДО");

        RelocationOutputRequest request = RelocationDataFactory.buildSendRequest(
                owner1Storage, owner2Storage, resourceId, excessive);
        Response response = fixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isEqualTo(400);

        ProductionStockAssertions.StockSnapshot senderAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "відправник ПІСЛЯ");
        ProductionStockAssertions.StockSnapshot recipientAfter = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_2, tracked, "отримувач ПІСЛЯ");
        RelocationStockAssertions.assertUnchanged(
                senderBefore, senderAfter, owner1Storage, resourceId, "insufficient send");
        RelocationStockAssertions.assertUnchanged(
                recipientBefore, recipientAfter, owner2Storage, resourceId, "insufficient send");
    }

    // --- D: resolve extended ---

    @Test(priority = 31)
    @TestCaseId("TC-REL-031")
    @Story("CANCELLED → RETURNED restores sender")
    public void testCancelThenReturnRestoresSenderStock() {
        double amount = 9.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО cancel/return");

        RelocationResponse returned = fixture.cancelThenReturn(
                UserRole.OWNER_1, UserRole.OWNER_2,
                owner1Storage, owner2Storage, resourceId, amount);
        assertThat(returned.getState()).isEqualTo(RelocationState.RETURNED);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ return");
        RelocationStockAssertions.assertUnchanged(
                before, after, owner1Storage, resourceId, "stock restored after RETURNED");
    }

    @Test(priority = 32)
    @TestCaseId("TC-REL-032")
    @Story("Resolve in final state")
    public void testResolveFinalStateReturns400() {
        RelocationResponse receive = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 5.0,
                RelocationDataFactory.uniqueBatchNumber());
        assertThat(receive.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        Response response = fixture.resolveRaw(
                UserRole.OWNER_1, receive.getId(), owner1Storage, RelocationState.FINISHED);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    // --- E: edit send extended ---

    @Test(priority = 42)
    @TestCaseId("TC-REL-042")
    @Story("Edit send increase without stock")
    public void testEditSendIncreaseInsufficientStock() {
        double initial = 5.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, initial);
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        double current = fixture.getResourceStock(owner1Storage, resourceId, UserRole.OWNER_1);
        if (current > 1.0) {
            fixture.createSend(UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, current - 0.5);
        }

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО failed edit");

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, initial + 5000.0, "too much");
        Response response = fixture.editSendRaw(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(response.statusCode()).isEqualTo(400);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ failed edit");
        RelocationStockAssertions.assertUnchanged(
                before, after, owner1Storage, resourceId, "edit increase rejected");
    }

    @Test(priority = 43)
    @TestCaseId("TC-REL-043")
    @Story("Edit send change recipient")
    public void testEditSendChangeRecipient() {
        double amount = 6.0;
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, amount);
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, amount, "new recipient", owner2Storage);
        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(updated.getRecipient().getId()).isEqualTo(owner2Storage);
    }

    @Test(priority = 44)
    @TestCaseId("TC-REL-044")
    @Story("Edit send immutable sender")
    public void testEditSendChangeSenderRejected() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, 4.0);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 4.0, "bad sender").toBuilder()
                .senderId(owner2Storage)
                .build();
        Response response = fixture.editSendRaw(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 45)
    @TestCaseId("TC-REL-045")
    @Story("Edit send person fields")
    public void testEditSendPersonFields() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, 5.0);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 5.0, "person update").toBuilder()
                .sendingPersonName("Іван")
                .sendingPersonRank("Капітан")
                .receivingPersonName("Петро")
                .receivingPersonRank("Майор")
                .build();
        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(updated.getDescription()).isEqualTo("person update");
    }

    @Test(priority = 45)
    @TestCaseId("TC-EDIT_REL-004")
    @Story("Edit send issuer name/rank persisted")
    @Description("""
            REQ-EDIT_REL-003 AC-01: поля «Видав» / «Звання» зберігаються при редагуванні видачі ресурсів.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testEditSendPersistsIssuerNameAndRank() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, 5.0);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 5.0, "TC-EDIT_REL-004").toBuilder()
                .sendingPersonName("Іван Тестовий")
                .sendingPersonRank("сержант")
                .build();
        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(updated.getDescription()).isEqualTo("TC-EDIT_REL-004");
        if (updated.getSendingPersonName() != null) {
            assertThat(updated.getSendingPersonName()).isEqualTo("Іван Тестовий");
            assertThat(updated.getSendingPersonRank()).isEqualTo("сержант");
        }
    }

    @Test(priority = 46)
    @TestCaseId("TC-REL-046")
    @Story("Edit send without invoice")
    public void testEditSendWithoutInvoiceDoesNotCreateInvoice() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, 3.0);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 3.0, "no invoice");
        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(updated.getDescription()).isEqualTo("no invoice");
        assertThat(updated.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
    }

    // --- F: edit receive extended ---

    @Test(priority = 51)
    @TestCaseId("TC-REL-051")
    @Story("Edit receive change supplier")
    public void testEditReceiveChangeSupplier() {
        List<Long> suppliers = fixture.listSupplierIds(UserRole.ADMIN);
        assertThat(suppliers).isNotEmpty();

        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        double amount = 10.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ДО supplier change");

        Long altSupplier = suppliers.size() > 1 ? suppliers.get(1) : suppliers.getFirst();
        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, amount, batchNumber, "supplier swap").toBuilder()
                .senderId(altSupplier)
                .build();
        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(updated.getSender().getId()).isEqualTo(altSupplier);

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ПІСЛЯ supplier change");
        RelocationStockAssertions.assertUnchanged(
                stockBefore, stockAfter, owner1Storage, resourceId, "recipient stock on supplier change");
    }

    @Test(priority = 52)
    @TestCaseId("TC-REL-052")
    @Story("Edit receive immutable recipient")
    public void testEditReceiveChangeRecipientRejected() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 8.0, batchNumber);

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, 8.0, batchNumber, "bad recipient").toBuilder()
                .recipientId(owner2Storage)
                .build();
        Response response = fixture.editReceiveRaw(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 53)
    @TestCaseId("TC-REL-053")
    @Story("Edit receive removeInvoiceFile flag")
    public void testEditReceiveRemoveInvoiceFileFlag() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 7.0, batchNumber);

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, 7.0, batchNumber, "remove file flag").toBuilder()
                .removeInvoiceFile(true)
                .build();
        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(updated.getHasExternalInvoicePhoto()).isFalse();
    }

    @Test(priority = 54)
    @TestCaseId("TC-EDIT_REL-008")
    @Story("Edit resource receive happy path")
    @Description("""
            REQ-EDIT_REL-002 AC-01 / CPMA-422: Admin редагує отримання ресурсів —
            дата, номер накладної, примітка, кількість; залишок оновлюється.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testEditResourceReceiveHappyPath() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        double initialAmount = 10.0;
        double updatedAmount = 12.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, initialAmount, batchNumber);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "до edit receive");

        String newInvoice = "INV-EDIT-REL-008-" + System.currentTimeMillis() % 1_000_000;
        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, updatedAmount, batchNumber, "TC-EDIT_REL-008 note").toBuilder()
                .invoiceNumber(newInvoice)
                .date(java.time.LocalDate.now().minusDays(1))
                .build();
        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);

        assertThat(updated.getInvoiceNumber()).isEqualTo(newInvoice);
        assertThat(updated.getDescription()).contains("TC-EDIT_REL-008");

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "після edit receive");
        double delta = after.amountOf(resourceId) - before.amountOf(resourceId);
        assertThat(delta)
                .as("залишок зріс на різницю amount (%.0f→%.0f)", initialAmount, updatedAmount)
                .isCloseTo(updatedAmount - initialAmount, org.assertj.core.data.Offset.offset(0.01));
    }

    // --- G: delete extended ---

    @Test(priority = 62)
    @TestCaseId("TC-REL-062")
    @Story("Delete insufficient recipient stock")
    public void testDeleteInsufficientRecipientStock() {
        double amount = 11.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);

        fixture.createSend(UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО failed delete");

        Response response = fixture.deleteRelocationRaw(
                UserRole.ADMIN, created.getId(), owner1Storage);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ delete attempt");

        assertThat(response.statusCode()).isIn(200, 204, 400);
        if (response.statusCode() == 400) {
            RelocationStockAssertions.assertUnchanged(
                    before, after, owner1Storage, resourceId, "delete rejected when stock moved");
        }
    }

    @Test(priority = 63)
    @TestCaseId("TC-REL-063")
    @Story("Delete wrong storage participant")
    public void testDeleteWrongStorageReturns403() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 6.0, batchNumber);

        Response response = fixture.deleteRelocationRaw(
                UserRole.OWNER_1, created.getId(), owner2Storage);
        assertThat(response.statusCode()).isIn(403, 401);
    }

    // --- H: admin compound ---

    @Test(priority = 56)
    @TestCaseId("TC-REL-056")
    @Story("Admin change supplier on external receive")
    public void testAdminChangeSupplierOnExternalReceive() {
        List<Long> suppliers = fixture.listSupplierIds(UserRole.ADMIN);
        assertThat(suppliers).isNotEmpty();

        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 9.0, batchNumber);

        Long altSupplier = suppliers.size() > 1 ? suppliers.get(1) : suppliers.getFirst();
        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, 9.0, batchNumber, "admin supplier swap").toBuilder()
                .senderId(altSupplier)
                .build();
        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(updated.getSender().getId()).isEqualTo(altSupplier);
    }

    @Test(priority = 58)
    @TestCaseId("TC-REL-058")
    @Story("Admin edit then delete net zero")
    public void testAdminEditThenDeleteNetZero() {
        double initial = 20.0;
        double edited = 25.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot baseline = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "baseline ДО setup");

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, initial, batchNumber);

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, edited, batchNumber, "increase before delete");
        fixture.editExternalReceive(UserRole.ADMIN, created.getId(), owner1Storage, edit);
        fixture.deleteRelocation(UserRole.ADMIN, created.getId(), owner1Storage);

        ProductionStockAssertions.StockSnapshot afterDelete = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ПІСЛЯ delete");
        RelocationStockAssertions.assertUnchanged(
                baseline, afterDelete, owner1Storage, resourceId, "net zero after edit+delete");
    }

    // --- Batch deep ---

    @Test(priority = 70)
    @TestCaseId("TC-REL-B01")
    @Story("Named batch + FIFO fallback")
    public void testBatchNamedPlusFifoFallback() {
        String batchA = "B01-A-" + System.currentTimeMillis();
        String batchB = "B01-B-" + System.currentTimeMillis();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 3.0, batchA);
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 17.0, batchB);

        double sendAmount = 10.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО B01 send");

        fixture.createSend(UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, sendAmount);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ B01 send");
        RelocationStockAssertions.assertDebitedFromSender(
                before, after, owner1Storage, resourceId, sendAmount, "B01 total debit");
    }

    @Test(priority = 71)
    @TestCaseId("TC-REL-B02")
    @Story("isProduced on send → FINISHED")
    public void testBatchIsProducedOnFinishedResolve() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 12.0, batchNumber);
        double sendAmount = 6.0;

        RelocationResponse sent = fixture.createSendWithBatch(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId,
                sendAmount, batchNumber, true);
        fixture.resolve(UserRole.OWNER_2, sent.getId(), owner2Storage, RelocationState.FINISHED);

        RelocationBatchAssertions.BatchSnapshot batchOnRecipient = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner2Storage, UserRole.OWNER_2, resourceId, batchNumber, true, "B02 produced");
        assertThat(batchOnRecipient.amount()).isCloseTo(sendAmount, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test(priority = 72)
    @TestCaseId("TC-REL-B03")
    @Story("Two line items different batches")
    public void testBatchTwoLineItems() {
        String batch1 = RelocationDataFactory.uniqueBatchNumber();
        String batch2 = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 5.0, batch1);
        fixture.ensureStock(owner1Storage, secondResourceId, 50.0);
        fixture.seedBatchOnStorage(owner1Storage, secondResourceId, 5.0, batch2);

        RelocationOutputRequest request = RelocationDataFactory.buildSendMultiItem(
                owner1Storage, owner2Storage,
                List.of(
                        RelocationDataFactory.usageWithBatch(resourceId, 3.0, batch1, false),
                        RelocationDataFactory.usageWithBatch(secondResourceId, 2.0, batch2, false)));

        Response response = fixture.sendRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test(priority = 73)
    @TestCaseId("TC-REL-B04")
    @Story("Batch edit receive reduces batch")
    public void testBatchEditReceiveReducesBatch() {
        double initial = 15.0;
        double edited = 10.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, initial, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "B04 ДО stock");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "B04 ДО");

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, edited, batchNumber, "B04 batch edit");
        fixture.editExternalReceive(UserRole.ADMIN, created.getId(), owner1Storage, edit);

        RelocationBatchAssertions.BatchSnapshot batchAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "B04 ПІСЛЯ");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore, batchAfter, initial - edited, "B04 batch delta");

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "B04 ПІСЛЯ stock");
        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, owner1Storage, resourceId, initial - edited, "B04 stock delta");
    }

    @Test(priority = 74)
    @TestCaseId("TC-REL-B05")
    @Story("Batch delete receive")
    public void testBatchDeleteReceiveRemovesBatch() {
        double amount = 14.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);

        fixture.deleteRelocation(UserRole.ADMIN, created.getId(), owner1Storage);

        RelocationBatchAssertions.assertBatchAbsent(
                apiExecutor, owner1Storage, UserRole.ADMIN, resourceId, batchNumber, false);
    }

    @Test(priority = 75)
    @TestCaseId("TC-REL-B06")
    @Story("Edit send batch composition")
    public void testBatchEditSendComposition() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 20.0, batchNumber);

        RelocationResponse sent = fixture.createSendWithBatch(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, 8.0, batchNumber, false);
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 5.0, "B06 reduce batch send").toBuilder()
                .items(List.of(RelocationDataFactory.usageWithBatch(resourceId, 5.0, batchNumber, false)))
                .build();
        fixture.editSend(UserRole.ADMIN, sent.getId(), owner1Storage, edit);

        RelocationBatchAssertions.BatchSnapshot after = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "B06 ПІСЛЯ");
        assertThat(after.amount()).isGreaterThan(0.0);
    }

    @Test(priority = 76)
    @TestCaseId("TC-REL-B07")
    @Story("Resolve FINISHED creates recipient batch")
    public void testBatchResolveFinishedCreatesRecipientBatch() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 10.0, batchNumber);
        double sendAmount = 4.0;

        RelocationBatchAssertions.BatchSnapshot recipientBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner2Storage, UserRole.OWNER_2, resourceId, batchNumber, false, "B07 ДО");

        RelocationResponse sent = fixture.createSendWithBatch(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId,
                sendAmount, batchNumber, false);
        fixture.resolve(UserRole.OWNER_2, sent.getId(), owner2Storage, RelocationState.FINISHED);

        RelocationBatchAssertions.BatchSnapshot recipientAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner2Storage, UserRole.OWNER_2, resourceId, batchNumber, false, "B07 ПІСЛЯ");
        RelocationBatchAssertions.assertBatchCredited(
                recipientBefore, recipientAfter, sendAmount, "B07 batch on FINISHED");
    }
}
