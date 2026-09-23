package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationItemBatchResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Batch accounting name and cost")
@Slf4j
public class RelocationBatchAccountingTest extends BaseFunctionalTest {

    private final List<Long> reconciliationIds = new ArrayList<>();
    private final List<Long> relocationIds = new ArrayList<>();

    private RelocationFixture relocationFixture;
    private FaitaResourceFixture reconciliationFixture;
    private InventoryFixture inventoryFixture;
    private Long storageId;
    private Long supplierId;
    private Long resourceId;
    private Long secondResourceId;
    private String accountingIdA;
    private String accountingIdB;
    private String normalizedAccountingId;
    private String sharedAccountingId;
    private String spacedAccountingId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareAccountingData() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        reconciliationFixture = new FaitaResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        secondResourceId = relocationFixture.secondResourceId();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        accountingIdA = "acc-cable-a-" + suffix;
        accountingIdB = "acc-cable-b-" + suffix;
        normalizedAccountingId = "acc-cable-normalized-" + suffix;
        sharedAccountingId = "acc-shared-" + suffix;
        spacedAccountingId = "acc-spaced-" + suffix;

        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingIdA, "Кабель силовий " + suffix, resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingIdB, "Комплект кабельний " + suffix, resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                normalizedAccountingId, "  кАбЕлЬ СиЛоВиЙ " + suffix + "  ", resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                sharedAccountingId, "Спільна бухгалтерська назва " + suffix,
                resourceId, secondResourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                spacedAccountingId, "  Назва з пробілами / №1 " + suffix + "  ", resourceId));
    }

    @AfterClass(alwaysRun = true)
    public void deleteAccountingData() {
        if (relocationFixture != null) {
            for (Long relocationId : relocationIds) {
                try {
                    relocationFixture.deleteRelocationRaw(UserRole.ADMIN, relocationId, storageId);
                } catch (Exception e) {
                    log.warn("Failed to delete test relocation id={}: {}", relocationId, e.getMessage());
                }
            }
        }
        if (reconciliationFixture != null) {
            reconciliationFixture.deleteReconciliationsQuietly(reconciliationIds);
        }
    }

    @Test(priority = 10)
    @TestCaseId({"TC-REL-ACC-001", "TC-REL-ACC-010"})
    @Story("Persist accounting attributes on a received batch")
    @Severity(SeverityLevel.BLOCKER)
    public void accountingNameAndFullCostArePersisted() {
        double stockBefore = stock(resourceId);
        BigDecimal paidAmount = new BigDecimal("1200.00");

        RelocationResponse created = receive(List.of(accountingUsage(
                resourceId, 10.0, accountingIdA, paidAmount)));

        RelocationItemBatchResponse batch = requireBatch(created, accountingIdA);
        assertThat(batch.getPaidAmount()).isEqualByComparingTo(paidAmount);
        assertThat(batch.getAmount()).isEqualByComparingTo("10.0");
        assertThat(batch.getBatchNumber()).isNotBlank();
        assertThat(stock(resourceId) - stockBefore).isEqualTo(10.0);
    }

    @Test(priority = 20)
    @TestCaseId("TC-REL-ACC-002")
    @Story("Two accounting rows for the same resource")
    @Severity(SeverityLevel.BLOCKER)
    public void sameResourceWithDifferentAccountingNamesCreatesSeparateBatches() {
        double stockBefore = stock(resourceId);

        RelocationResponse created = receive(List.of(
                accountingUsage(resourceId, 10.0, accountingIdA, new BigDecimal("1200.00")),
                accountingUsage(resourceId, 5.0, accountingIdB, new BigDecimal("900.00"))));

        assertThat(requireBatch(created, accountingIdA).getPaidAmount())
                .isEqualByComparingTo("1200.00");
        assertThat(requireBatch(created, accountingIdB).getPaidAmount())
                .isEqualByComparingTo("900.00");
        assertThat(allBatches(created).stream()
                .filter(batch -> accountingIdA.equals(batch.getAccResourceId())
                        || accountingIdB.equals(batch.getAccResourceId())))
                .hasSize(2);
        assertThat(stock(resourceId) - stockBefore).isEqualTo(15.0);
    }

    @Test(priority = 30)
    @TestCaseId({"TC-REL-ACC-003", "TC-REL-ACC-014"})
    @Story("Normalized accounting name is unique within relocation and resource")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Регістр, крайні пробіли та різні batchNumber не дозволяють обійти унікальність назви.")
    public void duplicateAccountingNameAfterTrimAndCaseIsRejectedAtomically() {
        double stockBefore = stock(resourceId);
        RelocationInputRequest request = receiveRequest(List.of(
                accountingUsage(resourceId, 2.0, accountingIdA, new BigDecimal("10.00")),
                accountingUsage(resourceId, 3.0, normalizedAccountingId, new BigDecimal("20.00"))));

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        double stockAfterRequest = stock(resourceId);
        cleanupUnexpectedSuccess(response);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("Дубль resource + normalized accountingName має повертати validation error. Body: %s",
                            response.getBody().asString())
                    .isEqualTo(400);
            softly.assertThat(response.getBody().asString())
                    .as("Негативний тест не повинен проходити через невалідний sender fixture")
                    .doesNotContain("\"field\":\"senderId\"");
            softly.assertThat(stockAfterRequest).isEqualTo(stockBefore);
        });
    }

    @Test(priority = 40)
    @TestCaseId("TC-REL-ACC-004")
    @Story("The same accounting name is allowed for different resources")
    public void sameAccountingNameForDifferentResourcesIsAllowed() {
        double firstBefore = stock(resourceId);
        double secondBefore = stock(secondResourceId);

        RelocationResponse created = receive(List.of(
                accountingUsage(resourceId, 2.0, sharedAccountingId, new BigDecimal("40.00")),
                accountingUsage(secondResourceId, 3.0, sharedAccountingId, new BigDecimal("60.00"))));

        assertThat(allBatches(created).stream()
                .filter(batch -> sharedAccountingId.equals(batch.getAccResourceId())))
                .hasSize(2);
        assertThat(stock(resourceId) - firstBefore).isEqualTo(2.0);
        assertThat(stock(secondResourceId) - secondBefore).isEqualTo(3.0);
    }

    @Test(priority = 50)
    @TestCaseId("TC-REL-ACC-006")
    @Story("Accounting fields are optional independently")
    public void accountingFieldsAreOptional() {
        RelocationResponse bothEmpty = receive(List.of(accountingUsage(
                resourceId, 1.0, null, null)));
        RelocationItemBatchResponse emptyBatch = allBatches(bothEmpty).getFirst();
        assertThat(emptyBatch.getAccResourceId()).isNull();
        assertThat(emptyBatch.getPaidAmount()).isNull();

        RelocationResponse nameOnly = receive(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, null)));
        RelocationItemBatchResponse nameOnlyBatch = requireBatch(nameOnly, accountingIdA);
        assertThat(nameOnlyBatch.getPaidAmount()).isNull();

        RelocationResponse costOnly = receive(List.of(accountingUsage(
                resourceId, 1.0, null, new BigDecimal("0.10"))));
        RelocationItemBatchResponse costOnlyBatch = allBatches(costOnly).getFirst();
        assertThat(costOnlyBatch.getAccResourceId()).isNull();
        assertThat(costOnlyBatch.getPaidAmount()).isEqualByComparingTo("0.10");
    }

    @Test(priority = 60)
    @TestCaseId("TC-REL-ACC-012")
    @Story("Zero is a valid full cost")
    public void zeroPaidAmountIsAccepted() {
        RelocationResponse created = receive(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, new BigDecimal("0.00"))));

        assertThat(requireBatch(created, accountingIdA).getPaidAmount())
                .isEqualByComparingTo("0.00");
    }

    @Test(priority = 61)
    @TestCaseId("TC-REL-ACC-012")
    @Story("Negative full cost is rejected")
    public void negativePaidAmountIsRejectedWithoutStockChange() {
        assertInvalidPaidAmount(new BigDecimal("-0.01"));
    }

    @Test(priority = 62)
    @TestCaseId("TC-REL-ACC-012")
    @Story("Full cost supports no more than two decimal places")
    public void paidAmountWithThreeDecimalPlacesIsRejectedWithoutStockChange() {
        assertInvalidPaidAmount(new BigDecimal("0.001"));
    }

    @Test(priority = 70)
    @TestCaseId("TC-REL-ACC-005")
    @Story("Accounting uniqueness is scoped to one relocation")
    public void sameResourceAndAccountingNameCanRepeatAcrossRelocations() {
        RelocationResponse first = receive(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, new BigDecimal("10.00"))));
        RelocationResponse second = receive(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, new BigDecimal("20.00"))));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(requireBatch(first, accountingIdA).getPaidAmount())
                .isEqualByComparingTo("10.00");
        assertThat(requireBatch(second, accountingIdA).getPaidAmount())
                .isEqualByComparingTo("20.00");
    }

    @Test(priority = 71)
    @TestCaseId("TC-REL-ACC-007")
    @Story("Unnamed accounting rows share one normalized empty key")
    @Severity(SeverityLevel.CRITICAL)
    public void duplicateUnnamedRowsAreRejectedAtomically() {
        double stockBefore = stock(resourceId);
        RelocationInputRequest request = receiveRequest(List.of(
                accountingUsage(resourceId, 2.0, null, null),
                accountingUsage(resourceId, 3.0, null, new BigDecimal("5.00"))));

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        double stockAfterRequest = stock(resourceId);
        cleanupUnexpectedSuccess(response);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("Два безіменні бухгалтерські рядки одного ресурсу мають бути дублікатом. Body: %s",
                            response.getBody().asString())
                    .isEqualTo(400);
            softly.assertThat(stockAfterRequest).isEqualTo(stockBefore);
        });
    }

    @Test(priority = 72)
    @TestCaseId("TC-REL-ACC-008")
    @Story("Deleting a multi-row accounting receive reverts every row")
    @Severity(SeverityLevel.CRITICAL)
    public void deletingAccountingReceiveRevertsAllRows() {
        double stockBefore = stock(resourceId);
        RelocationResponse created = receive(List.of(
                accountingUsage(resourceId, 2.0, accountingIdA, new BigDecimal("10.00")),
                accountingUsage(resourceId, 3.0, accountingIdB, new BigDecimal("20.00"))));
        assertThat(stock(resourceId) - stockBefore).isEqualTo(5.0);

        Response deleted = relocationFixture.deleteRelocationRaw(
                UserRole.ADMIN, created.getId(), storageId);
        assertThat(deleted.statusCode())
                .as("DELETE accounting relocation. Body: %s", deleted.getBody().asString())
                .isBetween(200, 299);
        relocationIds.remove(created.getId());

        assertThat(stock(resourceId)).isEqualTo(stockBefore);
    }

    @Test(priority = 73)
    @TestCaseId("TC-REL-ACC-009")
    @Story("One invalid row rejects the whole multi-resource receive")
    @Severity(SeverityLevel.BLOCKER)
    public void invalidSecondResourceRejectsWholeRequestAtomically() {
        double firstBefore = stock(resourceId);
        double secondBefore = stock(secondResourceId);
        RelocationInputRequest request = receiveRequest(List.of(
                accountingUsage(resourceId, 2.0, accountingIdA, new BigDecimal("10.00")),
                accountingUsage(secondResourceId, 3.0, sharedAccountingId, new BigDecimal("-0.01"))));

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        double firstAfterRequest = stock(resourceId);
        double secondAfterRequest = stock(secondResourceId);
        cleanupUnexpectedSuccess(response);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("Невалідний другий рядок має відхилити весь запит HTTP 400. Body: %s",
                            response.getBody().asString())
                    .isEqualTo(400);
            softly.assertThat(firstAfterRequest).isEqualTo(firstBefore);
            softly.assertThat(secondAfterRequest).isEqualTo(secondBefore);
        });
    }

    @Test(priority = 74)
    @TestCaseId("TC-REL-ACC-011")
    @Story("Null, omitted fields and surrounding spaces remain compatible")
    @Description("JsonInclude.NON_NULL serializes the null DTO variant as an omitted field; a spaced externalName remains addressable by accResourceId.")
    public void nullOmittedAndSpacedAccountingValuesAreSupported() {
        RelocationResponse omitted = receive(List.of(accountingUsage(
                resourceId, 1.0, null, null)));
        RelocationItemBatchResponse omittedBatch = allBatches(omitted).getFirst();
        assertThat(omittedBatch.getAccResourceId()).isNull();
        assertThat(omittedBatch.getPaidAmount()).isNull();

        RelocationResponse spaced = receive(List.of(accountingUsage(
                resourceId, 1.0, spacedAccountingId, new BigDecimal("1.00"))));
        assertThat(requireBatch(spaced, spacedAccountingId).getPaidAmount())
                .isEqualByComparingTo("1.00");
    }

    @Test(priority = 75)
    @TestCaseId("TC-REL-ACC-013")
    @Story("Accounting name accepts long Unicode and special characters")
    public void longUnicodeAccountingNameCanBeUsedWithoutCorruption() {
        String externalId = reconciliationFixture.newExternalId("acc-long-");
        String externalName = "Бухгалтерська / Accounting №1 🙂 " + "ДовгаНазва".repeat(40);
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                externalId, externalName, resourceId));

        RelocationResponse created = receive(List.of(accountingUsage(
                resourceId, 1.0, externalId, new BigDecimal("123.45"))));

        RelocationItemBatchResponse batch = requireBatch(created, externalId);
        assertThat(batch.getAccResourceId()).isEqualTo(externalId);
        assertThat(batch.getPaidAmount()).isEqualByComparingTo("123.45");
    }

    @Test(priority = 76)
    @TestCaseId("TC-REL-ACC-015")
    @Story("Item amount must equal the sum of batch amounts")
    @Severity(SeverityLevel.CRITICAL)
    public void inconsistentItemAndBatchAmountsAreRejectedWithoutStockChange() {
        double stockBefore = stock(resourceId);
        RelocationItemBatchRequest batch = RelocationDataFactory.accountingBatch(
                RelocationDataFactory.uniqueBatchNumber(),
                4.0,
                false,
                accountingIdA,
                new BigDecimal("10.00"));
        ResourceUsageRequest inconsistent = ResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amount(new BigDecimal("10.0"))
                .batches(List.of(batch))
                .build();

        Response response = apiExecutor.executeRelocationReceive(
                receiveRequest(List.of(inconsistent)), UserRole.OWNER_1);
        double stockAfterRequest = stock(resourceId);
        cleanupUnexpectedSuccess(response);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("item.amount != sum(batch.amount) має повертати 400. Body: %s",
                            response.getBody().asString())
                    .isEqualTo(400);
            softly.assertThat(stockAfterRequest).isEqualTo(stockBefore);
        });
    }

    @Test(priority = 77)
    @TestCaseId("TC-REL-ACC-016")
    @Story("Editing non-accounting fields preserves batch accounting attributes")
    @Severity(SeverityLevel.CRITICAL)
    public void regularReceiveEditPreservesAccountingAttributes() {
        RelocationResponse created = receive(List.of(accountingUsage(
                resourceId, 2.0, accountingIdA, new BigDecimal("25.50"))));
        String batchNumber = requireBatch(created, accountingIdA).getBatchNumber();
        RelocationInputEditRequest edit = RelocationInputEditRequest.builder()
                .description("accounting attributes must survive description edit")
                .date(LocalDate.now())
                .items(List.of(RelocationDataFactory.usageWithBatch(
                        resourceId, 2.0, batchNumber, false)))
                .build();

        RelocationResponse updated = relocationFixture.editExternalReceive(
                UserRole.ADMIN, created.getId(), storageId, edit);

        RelocationItemBatchResponse batch = requireBatch(updated, accountingIdA);
        assertThat(batch.getPaidAmount()).isEqualByComparingTo("25.50");
        assertThat(batch.getBatchNumber()).isEqualTo(batchNumber);
    }

    @Test(priority = 78)
    @TestCaseId("TC-REL-ACC-017")
    @Story("A user without rights to the recipient storage cannot create accounting receive")
    @Severity(SeverityLevel.BLOCKER)
    public void foreignStorageUserCannotCreateOrReadAccountingReceive() {
        double stockBefore = stock(resourceId);
        RelocationInputRequest request = receiveRequest(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, new BigDecimal("10.00"))));

        Response createResponse = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_2);
        Response journalResponse = relocationFixture.getJournalPageResponse(
                RelocationJournalQuery.receivedHistoryUi(storageId), UserRole.OWNER_2);
        double stockAfterRequest = stock(resourceId);
        cleanupUnexpectedSuccess(createResponse);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(createResponse.statusCode())
                    .as("OWNER_2 не має створювати receive на складі OWNER_1. Body: %s",
                            createResponse.getBody().asString())
                    .isIn(401, 403);
            softly.assertThat(journalResponse.statusCode())
                    .as("Чужий журнал має бути заборонений або фільтрований")
                    .isIn(200, 401, 403);
            if (journalResponse.statusCode() == 200 && createResponse.statusCode() >= 200
                    && createResponse.statusCode() < 300) {
                Long createdId = createResponse.as(RelocationResponse.class).getId();
                softly.assertThat(journalResponse.getBody().asString())
                        .doesNotContain("\"id\":" + createdId);
            }
            softly.assertThat(stockAfterRequest).isEqualTo(stockBefore);
        });
    }

    @Test(priority = 79)
    @TestCaseId("TC-REL-ACC-018")
    @Story("Legacy receive without accounting attributes remains supported")
    public void legacyBatchWithoutAccountingFieldsStillWorks() {
        double stockBefore = stock(resourceId);
        ResourceUsageRequest legacy = RelocationDataFactory.usageWithBatch(
                resourceId, 2.0, RelocationDataFactory.uniqueBatchNumber(), false);

        RelocationResponse created = receive(List.of(legacy));
        RelocationItemBatchResponse batch = allBatches(created).getFirst();

        assertThat(batch.getAccResourceId()).isNull();
        assertThat(batch.getPaidAmount()).isNull();
        assertThat(stock(resourceId) - stockBefore).isEqualTo(2.0);
    }

    @Test(priority = 80)
    @TestCaseId("TC-REL-ACC-019")
    @Story("Accounting rows do not duplicate journal, inventory or exports")
    @Severity(SeverityLevel.CRITICAL)
    public void accountingRowsKeepJournalInventoryAndExportsConsistent() {
        double stockBefore = stock(resourceId);
        RelocationResponse created = receive(List.of(
                accountingUsage(resourceId, 2.0, accountingIdA, new BigDecimal("10.00")),
                accountingUsage(resourceId, 3.0, accountingIdB, new BigDecimal("20.00"))));

        RelocationJournalQuery query = RelocationJournalQuery.receivedHistoryUi(storageId)
                .toBuilder()
                .pageSize(100)
                .build();
        Response journalResponse = relocationFixture.getJournalPageResponse(query, UserRole.OWNER_1);
        assertThat(journalResponse.statusCode()).isEqualTo(200);
        List<RelocationResponse> journalRows = journalResponse.jsonPath()
                .getList("content", RelocationResponse.class);
        RelocationResponse journalRow = journalRows.stream()
                .filter(row -> created.getId().equals(row.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Створене accounting receive відсутнє у журналі id=" + created.getId()));
        BigDecimal journalAmount = journalRow.getItems().stream()
                .filter(item -> item.getResource() != null
                        && resourceId.equals(item.getResource().getId()))
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StorageItemResponse> inventoryRows = inventoryFixture.listItems(
                        storageId, UserRole.OWNER_1, Map.of("size", 1000, "page", 0))
                .stream()
                .filter(item -> item.getResource() != null
                        && resourceId.equals(item.getResource().getId()))
                .toList();
        Response relocationExport = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_EXPORT,
                UserRole.OWNER_1,
                Map.of("receiverIds", storageId));
        Response inventoryExport = inventoryFixture.exportRemainders(storageId, UserRole.OWNER_1);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(journalAmount).isEqualByComparingTo("5.0");
            softly.assertThat(journalRow.getInvoiceNumber()).isEqualTo(created.getInvoiceNumber());
            softly.assertThat(inventoryRows).hasSize(1);
            softly.assertThat(stock(resourceId) - stockBefore).isEqualTo(5.0);
            softly.assertThat(relocationExport.statusCode()).isEqualTo(200);
            softly.assertThat(relocationExport.asByteArray().length).isPositive();
            softly.assertThat(inventoryExport.statusCode()).isEqualTo(200);
            softly.assertThat(inventoryExport.asByteArray().length).isPositive();
        });
    }

    private void assertInvalidPaidAmount(BigDecimal paidAmount) {
        double stockBefore = stock(resourceId);
        RelocationInputRequest request = receiveRequest(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, paidAmount)));

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        double stockAfterRequest = stock(resourceId);
        cleanupUnexpectedSuccess(response);

        assertThat(response.statusCode())
                .as("Некоректна paidAmount=%s має повертати 400. Body: %s",
                        paidAmount, response.getBody().asString())
                .isEqualTo(400);
        assertThat(response.getBody().asString())
                .as("Негативний тест не повинен проходити через невалідний sender fixture")
                .doesNotContain("\"field\":\"senderId\"");
        assertThat(stockAfterRequest).isEqualTo(stockBefore);
    }

    private RelocationResponse receive(List<ResourceUsageRequest> items) {
        Response response = apiExecutor.executeRelocationReceive(
                receiveRequest(items), UserRole.OWNER_1);
        assertThat(response.statusCode())
                .as("POST /relocations/receive. Body: %s", response.getBody().asString())
                .isEqualTo(200);
        RelocationResponse relocation = response.as(RelocationResponse.class);
        relocationIds.add(relocation.getId());
        return relocation;
    }

    private RelocationInputRequest receiveRequest(List<ResourceUsageRequest> items) {
        return RelocationDataFactory.buildReceiveRequest(
                supplierId,
                storageId,
                items,
                "erp-auto-test batch accounting " + UUID.randomUUID());
    }

    private ResourceUsageRequest accountingUsage(Long targetResourceId,
                                                  double amount,
                                                  String accResourceId,
                                                  BigDecimal paidAmount) {
        return RelocationDataFactory.usageWithAccountingBatch(
                targetResourceId,
                amount,
                RelocationDataFactory.uniqueBatchNumber() + "-" + UUID.randomUUID(),
                accResourceId,
                paidAmount);
    }

    private RelocationItemBatchResponse requireBatch(RelocationResponse relocation, String accResourceId) {
        return allBatches(relocation).stream()
                .filter(batch -> accResourceId.equals(batch.getAccResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Response не містить batch з accResourceId=" + accResourceId));
    }

    private List<RelocationItemBatchResponse> allBatches(RelocationResponse relocation) {
        return relocation.getItems().stream()
                .flatMap(item -> item.getBatches().stream())
                .toList();
    }

    private double stock(Long targetResourceId) {
        return relocationFixture.getResourceStock(storageId, targetResourceId, UserRole.OWNER_1);
    }

    private void cleanupUnexpectedSuccess(Response response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        RelocationResponse relocation = response.as(RelocationResponse.class);
        if (relocation.getId() == null) {
            return;
        }
        Response deleted = relocationFixture.deleteRelocationRaw(
                UserRole.ADMIN, relocation.getId(), storageId);
        if (deleted.statusCode() < 200 || deleted.statusCode() >= 300) {
            relocationIds.add(relocation.getId());
        }
    }
}
