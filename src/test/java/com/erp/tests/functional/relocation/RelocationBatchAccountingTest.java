package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.RelocationItemBatchResponse;
import com.erp.models.response.RelocationResponse;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
    private Long storageId;
    private Long supplierId;
    private Long resourceId;
    private Long secondResourceId;
    private String accountingIdA;
    private String accountingIdB;
    private String normalizedAccountingId;
    private String sharedAccountingId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareAccountingData() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        reconciliationFixture = new FaitaResourceFixture(testContext, apiExecutor);
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

        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingIdA, "Кабель силовий " + suffix, resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingIdB, "Комплект кабельний " + suffix, resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                normalizedAccountingId, "  кАбЕлЬ СиЛоВиЙ " + suffix + "  ", resourceId));
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                sharedAccountingId, "Спільна бухгалтерська назва " + suffix,
                resourceId, secondResourceId));
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
    @TestCaseId("TC-REL-ACC-001")
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
        rememberCreatedRelocation(response);

        assertThat(response.statusCode())
                .as("Дубль resource + normalized accountingName має повертати validation error. Body: %s",
                        response.getBody().asString())
                .isEqualTo(400);
        assertThat(response.getBody().asString())
                .as("Негативний тест не повинен проходити через невалідний sender fixture")
                .doesNotContain("\"field\":\"senderId\"");
        assertThat(stock(resourceId)).isEqualTo(stockBefore);
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

    private void assertInvalidPaidAmount(BigDecimal paidAmount) {
        double stockBefore = stock(resourceId);
        RelocationInputRequest request = receiveRequest(List.of(accountingUsage(
                resourceId, 1.0, accountingIdA, paidAmount)));

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        rememberCreatedRelocation(response);

        assertThat(response.statusCode())
                .as("Некоректна paidAmount=%s має повертати 400. Body: %s",
                        paidAmount, response.getBody().asString())
                .isEqualTo(400);
        assertThat(response.getBody().asString())
                .as("Негативний тест не повинен проходити через невалідний sender fixture")
                .doesNotContain("\"field\":\"senderId\"");
        assertThat(stock(resourceId)).isEqualTo(stockBefore);
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

    private void rememberCreatedRelocation(Response response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            RelocationResponse relocation = response.as(RelocationResponse.class);
            if (relocation.getId() != null) {
                relocationIds.add(relocation.getId());
            }
        }
    }
}
