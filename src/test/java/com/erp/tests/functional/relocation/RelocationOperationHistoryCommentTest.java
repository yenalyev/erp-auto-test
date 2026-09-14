package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceHistoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Notes in operation history")
public class RelocationOperationHistoryCommentTest extends BaseFunctionalTest {

    private RelocationFixture relocations;
    private InventoryFixture inventory;
    private ResourceFixture resources;
    private long senderId;
    private long recipientId;
    private long supplierId;
    private ResourceResponse resource;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepare() {
        relocations = new RelocationFixture(testContext, apiExecutor);
        inventory = new InventoryFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        relocations.prepareContext();
        senderId = ConfigProvider.getOwner1StorageId();
        recipientId = ConfigProvider.getOwner2StorageId();
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
    }

    @BeforeMethod(alwaysRun = true)
    public void createIsolatedResource() {
        resource = resources.createUniqueResource("rel-history-note-");
    }

    @TestCaseId("TC-REL-HIST-CMT-001")
    @Test
    @Story("Edited send note updates the existing history row")
    @Severity(SeverityLevel.CRITICAL)
    public void editingSendNoteUpdatesExistingSenderHistory() {
        relocations.ensureStock(senderId, resource.getId(), 10.0);
        String beforeNote = marker("send-before");
        String afterNote = marker("send-after");
        double amount = 3.0;

        RelocationResponse sent = relocations.createSendWithDescription(
                UserRole.OWNER_1, senderId, recipientId, resource.getId(), amount, beforeNote);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        List<ResourceHistoryResponse> before = history(senderId);
        ResourceHistoryResponse original = onlyRowWithComment(before, beforeNote);
        assertThat(original.getAmount()).isEqualTo(amount);

        relocations.editSend(UserRole.OWNER_1, sent.getId(), senderId,
                RelocationDataFactory.buildSendEditRequest(resource.getId(), amount, afterNote));

        PollUtils.waitUntilTrue(() -> hasComment(senderId, afterNote), 15_000,
                "Edited send note in operation history");
        List<ResourceHistoryResponse> after = history(senderId);
        assertSameRowWithUpdatedComment(before, after, original, beforeNote, afterNote);
    }

    @TestCaseId("TC-REL-HIST-CMT-002")
    @Test
    @Story("Edited external receive note updates the existing history row")
    @Severity(SeverityLevel.CRITICAL)
    public void editingReceiveNoteUpdatesExistingRecipientHistory() {
        String beforeNote = marker("receive-before");
        String afterNote = marker("receive-after");
        String batch = RelocationDataFactory.uniqueBatchNumber();
        double amount = 4.0;
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                supplierId, senderId, resource.getId(), amount, batch)
                .toBuilder().description(beforeNote).build();
        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        assertThat(response.statusCode()).isBetween(200, 299);
        RelocationResponse received = response.as(RelocationResponse.class);
        List<ResourceHistoryResponse> before = history(senderId);
        ResourceHistoryResponse original = onlyRowWithComment(before, beforeNote);
        assertThat(original.getAmount()).isEqualTo(amount);

        relocations.editExternalReceive(UserRole.ADMIN, received.getId(), senderId,
                RelocationDataFactory.buildReceiveEditRequest(
                        resource.getId(), amount, batch, afterNote));

        PollUtils.waitUntilTrue(() -> hasComment(senderId, afterNote), 15_000,
                "Edited receive note in operation history");
        List<ResourceHistoryResponse> after = history(senderId);
        assertSameRowWithUpdatedComment(before, after, original, beforeNote, afterNote);
    }

    @TestCaseId("TC-REL-HIST-CMT-003")
    @Test
    @Story("Accepted relocation note appears in recipient history")
    @Severity(SeverityLevel.CRITICAL)
    public void acceptedSendNoteAppearsInRecipientHistory() {
        relocations.ensureStock(senderId, resource.getId(), 10.0);
        String note = marker("accepted");
        RelocationResponse sent = relocations.createSendWithDescription(
                UserRole.OWNER_1, senderId, recipientId, resource.getId(), 2.0, note);

        relocations.resolve(UserRole.OWNER_2, sent.getId(), recipientId,
                RelocationState.FINISHED, note);

        PollUtils.waitUntilTrue(() -> hasComment(recipientId, note), 15_000,
                "Accepted relocation note in recipient history");
        ResourceHistoryResponse row = onlyRowWithComment(history(recipientId), note);
        assertThat(row.getAmount()).isEqualTo(2.0);
    }

    @TestCaseId("TC-REL-HIST-CMT-004")
    @Test
    @Story("A multi-resource relocation updates the note on every history row")
    @Severity(SeverityLevel.CRITICAL)
    public void editingMultiResourceSendUpdatesEveryHistoryRow() {
        ResourceResponse secondResource = resources.createUniqueResource("rel-history-note-second-");
        relocations.ensureStock(senderId, resource.getId(), 10.0);
        relocations.ensureStock(senderId, secondResource.getId(), 10.0);
        String beforeNote = marker("multi-before");
        String afterNote = marker("multi-after");
        List<ResourceUsageRequest> items = List.of(
                RelocationDataFactory.usage(resource.getId(), 2.0),
                RelocationDataFactory.usage(secondResource.getId(), 3.0));

        RelocationResponse sent = relocations.createSendMultiItem(
                UserRole.OWNER_1, senderId, recipientId, items, beforeNote);
        List<ResourceHistoryResponse> firstBefore = history(senderId, resource.getId());
        List<ResourceHistoryResponse> secondBefore = history(senderId, secondResource.getId());
        ResourceHistoryResponse firstOriginal = onlyRowWithComment(firstBefore, beforeNote);
        ResourceHistoryResponse secondOriginal = onlyRowWithComment(secondBefore, beforeNote);

        relocations.editSend(UserRole.OWNER_1, sent.getId(), senderId,
                RelocationOutputEditRequest.builder()
                        .description(afterNote)
                        .date(LocalDate.now())
                        .items(items)
                        .build());

        PollUtils.waitUntilTrue(() -> hasComment(senderId, resource.getId(), afterNote)
                        && hasComment(senderId, secondResource.getId(), afterNote),
                15_000, "Edited multi-resource note in operation history");
        assertSameRowWithUpdatedComment(firstBefore, history(senderId, resource.getId()),
                firstOriginal, beforeNote, afterNote);
        assertSameRowWithUpdatedComment(secondBefore, history(senderId, secondResource.getId()),
                secondOriginal, beforeNote, afterNote);
    }

    private List<ResourceHistoryResponse> history(long storageId) {
        return history(storageId, resource.getId());
    }

    private List<ResourceHistoryResponse> history(long storageId, long resourceId) {
        return inventory.parseOperationHistory(
                inventory.getOperationHistoryToday(storageId, UserRole.ADMIN))
                .getOperationHistoryList().stream()
                .filter(row -> row.getResource() != null
                        && Objects.equals(row.getResource().getId(), resourceId))
                .toList();
    }

    private boolean hasComment(long storageId, String comment) {
        return history(storageId).stream().anyMatch(row -> comment.equals(row.getComment()));
    }

    private boolean hasComment(long storageId, long resourceId, String comment) {
        return history(storageId, resourceId).stream()
                .anyMatch(row -> comment.equals(row.getComment()));
    }

    private ResourceHistoryResponse onlyRowWithComment(List<ResourceHistoryResponse> rows, String comment) {
        List<ResourceHistoryResponse> matches = rows.stream()
                .filter(row -> comment.equals(row.getComment()))
                .toList();
        assertThat(matches).as("History row with comment %s", comment).hasSize(1);
        return matches.getFirst();
    }

    private void assertSameRowWithUpdatedComment(List<ResourceHistoryResponse> before,
                                                 List<ResourceHistoryResponse> after,
                                                 ResourceHistoryResponse original,
                                                 String oldComment,
                                                 String newComment) {
        assertThat(after).as("Editing a note must not add a history row")
                .hasSameSizeAs(before);
        assertThat(after).noneMatch(row -> oldComment.equals(row.getComment()));
        ResourceHistoryResponse updated = onlyRowWithComment(after, newComment);
        assertThat(updated.getResourceOperationType()).isEqualTo(original.getResourceOperationType());
        assertThat(updated.getAmount()).isEqualTo(original.getAmount());
        assertThat(updated.getFromUnit()).isEqualTo(original.getFromUnit());
        assertThat(updated.getToUnit()).isEqualTo(original.getToUnit());
        assertThat(updated.getDate()).isEqualTo(original.getDate());
    }

    private static String marker(String phase) {
        return "Примітка переміщення " + phase + " " + UUID.randomUUID();
    }
}
