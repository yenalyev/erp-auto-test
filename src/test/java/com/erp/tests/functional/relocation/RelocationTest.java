package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.response.PagedRelocationResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationBatchAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.validators.SchemaRegistry;
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
@Feature("Resource Relocations")
public class RelocationTest extends BaseFunctionalTest {

    private RelocationFixture fixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long supplierId;
    private Long unitStorageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupRelocationTests() {
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        unitStorageId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        fixture.ensureStock(owner1Storage, resourceId, 200.0);
    }

    // --- A: List / filters ---

    @Test(priority = 1)
    @TestCaseId("TC-REL-001")
    @Story("List relocations")
    @Severity(SeverityLevel.NORMAL)
    public void testGetRelocationsBySenderIds() {
        Response response = Allure.step("GET relocations by senderIds", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RELOCATION_GET_PAGE,
                        UserRole.OWNER_1,
                        Map.of("senderIds", owner1Storage, "size", 10)));
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_GET_PAGE);
        PagedRelocationResponse page = response.as(PagedRelocationResponse.class);
        assertThat(page.getContent()).isNotNull();
    }

    @Test(priority = 2)
    @TestCaseId("TC-REL-002")
    @Story("List relocations")
    public void testGetRelocationsByReceiverIds() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                UserRole.OWNER_1,
                Map.of("receiverIds", owner1Storage, "size", 10));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test(priority = 3)
    @TestCaseId("TC-REL-005")
    @Story("Creation options")
    public void testGetCreationOptions() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_GET_CREATION_OPTIONS,
                UserRole.OWNER_1,
                String.valueOf(owner1Storage));
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_GET_CREATION_OPTIONS);
    }

    // --- B: Send ---

    @Test(priority = 10)
    @TestCaseId("TC-REL-010")
    @Story("Send storage to storage")
    @Severity(SeverityLevel.CRITICAL)
    public void testSendStorageToStorage() {
        double amount = 5.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "відправник ДО send");
        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_1, tracked, "отримувач ДО send");

        RelocationResponse created = Allure.step("POST send", () ->
                fixture.createSend(UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount));

        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot senderAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "відправник ПІСЛЯ send");
        ProductionStockAssertions.StockSnapshot recipientAfter = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_1, tracked, "отримувач ПІСЛЯ send");

        RelocationStockAssertions.assertDebitedFromSender(
                senderBefore, senderAfter, owner1Storage, resourceId, amount, "CREATED send");
        RelocationStockAssertions.assertUnchanged(
                recipientBefore, recipientAfter, owner2Storage, resourceId, "recipient waits resolve");
    }

    @Test(priority = 11)
    @TestCaseId("TC-REL-011")
    @Story("Send with named batch")
    public void testSendWithNamedBatch() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        double seedAmount = 20.0;
        double sendAmount = 5.0;

        fixture.seedBatchOnStorage(owner1Storage, resourceId, seedAmount, batchNumber);
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО send");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ДО send");

        RelocationResponse created = fixture.createSendWithBatch(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId,
                sendAmount, batchNumber, false);
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot senderAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ send");
        RelocationBatchAssertions.BatchSnapshot batchAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ПІСЛЯ send");

        RelocationStockAssertions.assertDebitedFromSender(
                senderBefore, senderAfter, owner1Storage, resourceId, sendAmount, "named batch send");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore, batchAfter, sendAmount, "named batch debit");
    }

    @Test(priority = 12)
    @TestCaseId("TC-REL-013")
    @Story("Send to UNIT")
    public void testSendToUnitAutoFinished() {
        double amount = 10.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО send→UNIT");

        RelocationResponse created = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, amount);
        assertThat(created.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ send→UNIT");
        RelocationStockAssertions.assertDebitedFromSender(
                before, after, owner1Storage, resourceId, amount, "AUTO_FINISHED to UNIT");
    }

    // --- C: External receive ---

    @Test(priority = 20)
    @TestCaseId("TC-REL-020")
    @Story("External receive")
    @Severity(SeverityLevel.CRITICAL)
    public void testExternalReceive() {
        double amount = 15.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО receive");

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);
        assertThat(created.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ receive");
        RelocationStockAssertions.assertCreditedToRecipient(
                before, after, owner1Storage, resourceId, amount, "SUPPLIER→storage");
    }

    @Test(priority = 21)
    @TestCaseId("TC-REL-021")
    @Story("Receive creates batch")
    public void testReceiveCreatesBatch() {
        double amount = 12.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationBatchAssertions.BatchSnapshot before = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ДО receive");

        fixture.createExternalReceive(UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);

        RelocationBatchAssertions.BatchSnapshot after = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ПІСЛЯ receive");
        RelocationBatchAssertions.assertBatchCredited(before, after, amount, "new batch on receive");
    }

    @Test(priority = 22)
    @TestCaseId("TC-REL-022")
    @Story("Receive negative")
    public void testReceiveFromInternalStorageRejected() {
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                owner2Storage, owner1Storage, resourceId, 5.0, RelocationDataFactory.uniqueBatchNumber());
        Set<Long> tracked = fixture.trackedResource(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО invalid receive");

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        assertThat(response.statusCode()).isEqualTo(400);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ invalid receive");
        RelocationStockAssertions.assertUnchanged(
                before, after, owner1Storage, resourceId, "invalid receive from internal storage");
    }

    // --- D: Resolve ---

    @Test(priority = 30)
    @TestCaseId("TC-REL-030")
    @Story("Resolve FINISHED")
    @Severity(SeverityLevel.CRITICAL)
    public void testResolveFinished() {
        double amount = 7.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, amount);
        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_1, tracked, "отримувач ДО resolve");

        RelocationResponse resolved = fixture.resolve(
                UserRole.OWNER_1, sent.getId(), owner2Storage, RelocationState.FINISHED);
        assertThat(resolved.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot recipientAfter = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.OWNER_1, tracked, "отримувач ПІСЛЯ resolve");
        RelocationStockAssertions.assertCreditedToRecipient(
                recipientBefore, recipientAfter, owner2Storage, resourceId, amount, "FINISHED resolve");
    }

    // --- F: Edit external receive ---

    @Test(priority = 50)
    @TestCaseId("TC-REL-050")
    @Story("Edit external receive")
    public void testEditExternalReceiveReducesAmount() {
        double initial = 15.0;
        double edited = 10.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, initial, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО edit receive");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ДО edit");

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, edited, batchNumber, "updated by OWNER_1");
        fixture.editExternalReceive(UserRole.OWNER_1, created.getId(), owner1Storage, edit);

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ edit receive");
        RelocationBatchAssertions.BatchSnapshot batchAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.OWNER_1, resourceId, batchNumber, false, "ПІСЛЯ edit");

        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, owner1Storage, resourceId, initial - edited, "edit reduce amount");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore, batchAfter, initial - edited, "batch reduced on edit");
    }

    // --- H: Admin external edit/delete ---

    @Test(priority = 54)
    @TestCaseId("TC-REL-054")
    @Story("Admin edit external receive")
    @Severity(SeverityLevel.CRITICAL)
    public void testAdminEditExternalReceive() {
        double initial = 20.0;
        double edited = 12.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, initial, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ДО admin edit");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.ADMIN, resourceId, batchNumber, false, "ДО admin edit");

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, edited, batchNumber, "admin updated description");
        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(updated.getDescription()).contains("admin updated");

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ПІСЛЯ admin edit");
        RelocationBatchAssertions.BatchSnapshot batchAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.ADMIN, resourceId, batchNumber, false, "ПІСЛЯ admin edit");

        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, owner1Storage, resourceId, initial - edited, "admin edit −8");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore, batchAfter, initial - edited, "admin batch edit");
    }

    @Test(priority = 55)
    @TestCaseId("TC-REL-055")
    @Story("Admin edit invoice fields")
    public void testAdminEditInvoiceFields() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 10.0, batchNumber);

        RelocationInputEditRequest edit = RelocationInputEditRequest.builder()
                .description("invoice update")
                .date(java.time.LocalDate.now())
                .items(RelocationDataFactory.buildReceiveEditRequest(
                        resourceId, 10.0, batchNumber, "x").getItems())
                .invoiceNumber("ADMIN-INV-001")
                .isPaidByCash(true)
                .paidAmount(BigDecimal.valueOf(500))
                .build();

        RelocationResponse updated = fixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), owner1Storage, edit);
        assertThat(updated.getInvoiceNumber()).isEqualTo("ADMIN-INV-001");
        assertThat(updated.getIsPaidByCash()).isTrue();
    }

    @Test(priority = 57)
    @TestCaseId("TC-REL-057")
    @Story("Admin delete external receive")
    @Severity(SeverityLevel.CRITICAL)
    public void testAdminDeleteExternalReceive() {
        double amount = 15.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, amount, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ДО admin delete");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, owner1Storage, UserRole.ADMIN, resourceId, batchNumber, false, "ДО admin delete");

        fixture.deleteRelocation(UserRole.ADMIN, created.getId(), owner1Storage);

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.ADMIN, tracked, "ПІСЛЯ admin delete");
        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, owner1Storage, resourceId, amount, "full revert on delete");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore,
                RelocationBatchAssertions.captureBatch(
                        apiExecutor, owner1Storage, UserRole.ADMIN, resourceId, batchNumber, false, "ПІСЛЯ delete"),
                amount, "batch reverted");
    }

    @Test(priority = 59)
    @TestCaseId("TC-REL-059")
    @Story("RBAC negative")
    public void testOwner2CannotEditExternalReceive() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 10.0, batchNumber);

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, 8.0, batchNumber, "forbidden");
        Response response = apiExecutor.executeRelocationUpdateReceive(
                created.getId(), owner1Storage, edit, UserRole.OWNER_2);
        assertThat(response.statusCode()).isIn(403, 401);
    }

    // --- E: Edit send ---

    @Test(priority = 40)
    @TestCaseId("TC-REL-040")
    @Story("Edit send reduce amount")
    public void testEditSendReduceAmount() {
        double initial = 15.0;
        double edited = 10.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, unitStorageId, resourceId, initial);
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО edit send");

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, edited, "reduced send");
        fixture.editSend(UserRole.OWNER_1, sent.getId(), owner1Storage, edit);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ edit send");
        RelocationStockAssertions.assertCreditedToRecipient(
                before, after, owner1Storage, resourceId, initial - edited, "revert delta on send edit");
    }

    @Test(priority = 41)
    @TestCaseId("TC-REL-041")
    @Story("Edit wrong state")
    public void testEditSendWrongStateReturns400() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 5.0);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 3.0, "should fail");
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND,
                UserRole.OWNER_1, edit, sent.getId(), owner1Storage);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    // --- G: Delete ---

    @Test(priority = 60)
    @TestCaseId("TC-REL-060")
    @Story("Delete AUTO_FINISHED")
    public void testDeleteAutoFinishedReceive() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, owner1Storage, resourceId, 10.0, batchNumber);
        fixture.deleteRelocation(UserRole.OWNER_1, created.getId(), owner1Storage);
    }

    @Test(priority = 61)
    @TestCaseId("TC-REL-061")
    @Story("Delete wrong state")
    public void testDeleteCreatedReturns400() {
        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 5.0);
        Response response = apiExecutor.executeRelocationDelete(
                sent.getId(), owner1Storage, UserRole.OWNER_1);
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
