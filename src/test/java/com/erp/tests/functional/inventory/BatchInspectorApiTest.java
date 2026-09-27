package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.BatchInspectionResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.RelocationItemBatchResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-012 — Batch Inspector API")
public class BatchInspectorApiTest extends InventoryApiTestBase {

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private UserFixture userFixture;
    private UserFixture.BusinessActor locationOwner;

    @BeforeClass(alwaysRun = true)
    public void setupBatchInspectorFixtures() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse ownerStorage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, owner1StorageId);
        locationOwner = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(ownerStorage));
        apiExecutor.setSessionForRole(
                UserRole.DYNAMIC_LOCATION_OWNER, locationOwner.username(), locationOwner.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDynamicBatchInspectorUser() {
        apiExecutor.restoreDefaultSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER);
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-012-001")
    @Story("Stable UUID and external batch inspection")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Зовнішня партія отримує UUID, однаковий у relocation, inventory та Batch Inspector.
            Inspector знаходить партію за точною назвою, показує фактичний залишок і рух,
            а виробництво й техкарта для зовнішньої партії відсутні.
            """)
    public void externalBatchKeepsOneUuidAcrossContracts() {
        String batchName = "WMS12-EXT-" + System.currentTimeMillis();
        double amount = 7.0;
        RelocationResponse received = relocationFixture.createExternalReceive(
                UserRole.ADMIN, owner1StorageId, anchorResourceId, amount, batchName);

        try {
            UUID relocationUuid = requireRelocationBatch(received, batchName).getBatchUuid();
            assertThat(relocationUuid).as("UUID у relocation response").isNotNull();

            StorageItemBatchResponse inventoryBatch = requireStorageBatch(
                    owner1StorageId, anchorResourceId, batchName);
            assertThat(inventoryBatch.getBatchUuid())
                    .as("Inventory та relocation посилаються на одну логічну партію")
                    .isEqualTo(relocationUuid);

            BatchInspectionResponse inspection = requireInspection(batchName, anchorResourceId);
            assertThat(inspection.getId()).isEqualTo(relocationUuid);
            assertThat(inspection.getName()).isEqualTo(batchName);
            assertThat(inspection.getLegacy()).isFalse();
            assertThat(inspection.getProductions()).isEmpty();
            assertThat(inspection.getIngredients()).isEmpty();
            assertThat(inspection.getStock())
                    .anySatisfy(stock -> {
                        assertThat(stock.getStorage().getId()).isEqualTo(owner1StorageId);
                        assertThat(stock.getAmount().doubleValue()).isCloseTo(amount, within(0.001));
                    });
            assertThat(inspection.getMovements())
                    .anySatisfy(movement -> {
                        assertThat(movement.getRelocationId()).isEqualTo(received.getId());
                        assertThat(movement.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
                        assertThat(movement.getAmount().doubleValue()).isCloseTo(amount, within(0.001));
                    });
        } finally {
            deleteRelocationQuietly(received, owner1StorageId);
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-012-004")
    @Story("Partial relocation preserves UUID and distributes current stock")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Часткове переміщення не створює нову партію: sender, recipient та inspector
            використовують один UUID. Поточний залишок розподілений між двома складами,
            а історія містить проведене переміщення на фактичну кількість.
            """)
    public void partialRelocationPreservesUuidAndSplitsActualStock() {
        String batchName = "WMS12-MOVE-" + System.currentTimeMillis();
        double initialAmount = 11.0;
        double movedAmount = 4.0;
        RelocationResponse received = relocationFixture.createExternalReceive(
                UserRole.ADMIN, owner1StorageId, anchorResourceId, initialAmount, batchName);
        RelocationResponse sent = null;

        try {
            UUID expectedUuid = requireRelocationBatch(received, batchName).getBatchUuid();
            sent = relocationFixture.createSendWithBatch(
                    UserRole.ADMIN, owner1StorageId, owner2StorageId, anchorResourceId,
                    movedAmount, batchName, false);
            assertThat(requireRelocationBatch(sent, batchName).getBatchUuid())
                    .as("UUID не змінюється у вихідному relocation")
                    .isEqualTo(expectedUuid);
            relocationFixture.resolve(
                    UserRole.ADMIN, sent.getId(), owner2StorageId, RelocationState.FINISHED);

            assertThat(requireStorageBatch(owner1StorageId, anchorResourceId, batchName).getBatchUuid())
                    .isEqualTo(expectedUuid);
            assertThat(requireStorageBatch(owner2StorageId, anchorResourceId, batchName).getBatchUuid())
                    .isEqualTo(expectedUuid);

            BatchInspectionResponse inspection = requireInspection(batchName, anchorResourceId);
            assertThat(inspection.getId()).isEqualTo(expectedUuid);
            assertStock(inspection, owner1StorageId, initialAmount - movedAmount);
            assertStock(inspection, owner2StorageId, movedAmount);
            assertThat(inspection.getStock().stream()
                    .map(BatchInspectionResponse.Stock::getAmount)
                    .mapToDouble(BigDecimal::doubleValue)
                    .sum()).isCloseTo(initialAmount, within(0.001));
            Long sentId = sent.getId();
            assertThat(inspection.getMovements())
                    .anySatisfy(movement -> {
                        assertThat(movement.getRelocationId()).isEqualTo(sentId);
                        assertThat(movement.getState()).isEqualTo(RelocationState.FINISHED);
                        assertThat(movement.getSender().getId()).isEqualTo(owner1StorageId);
                        assertThat(movement.getRecipient().getId()).isEqualTo(owner2StorageId);
                        assertThat(movement.getAmount().doubleValue())
                                .isCloseTo(movedAmount, within(0.001));
                    });
            assertThat(inspection.getMovements())
                    .extracting(BatchInspectionResponse.Movement::getDateTime)
                    .isSorted();
        } finally {
            deleteRelocationQuietly(sent, owner1StorageId);
            deleteRelocationQuietly(received, owner1StorageId);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-012-005")
    @Story("New batch names are globally unique across resources")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після створення UUID-партії її назву не можна повторно використати для іншого
            ресурсу. Backend відхиляє друге отримання й inspector не змішує ресурси.
            """)
    public void sameNameCannotBeReusedForAnotherResource() {
        String batchName = "WMS12-SAME-" + System.currentTimeMillis();
        Long secondResourceId = relocationFixture.secondResourceId();
        RelocationResponse first = relocationFixture.createExternalReceive(
                UserRole.ADMIN, owner1StorageId, anchorResourceId, 3.0, batchName);

        try {
            Response duplicate = apiExecutor.executeRelocationReceive(
                    RelocationDataFactory.buildReceiveRequest(
                            testContext.get(com.erp.test_context.ContextKey.RELOCATION_SUPPLIER_ID),
                            owner1StorageId, secondResourceId, 5.0, batchName),
                    UserRole.ADMIN);
            assertThat(duplicate.statusCode()).isEqualTo(400);
            assertThat(duplicate.body().asString())
                    .contains("batchNumber", batchName, "уже належить іншому ресурсу");

            List<BatchInspectionResponse> results = inventoryFixture.inspectBatch(batchName, UserRole.ADMIN);
            assertThat(results).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(requireRelocationBatch(first, batchName).getBatchUuid());
                assertThat(item.getResource().getId()).isEqualTo(anchorResourceId);
                assertThat(item.getStock()).allSatisfy(stock ->
                        assertThat(stock.getResource().getId()).isEqualTo(anchorResourceId));
                assertThat(item.getMovements()).allSatisfy(movement ->
                        assertThat(movement.getResource().getId()).isEqualTo(anchorResourceId));
            });
        } finally {
            deleteRelocationQuietly(first, owner1StorageId);
        }
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-012-007")
    @Story("Admin-only API and empty search result")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Batch Inspector доступний лише глобальному Admin. Складський owner та анонімний
            клієнт не отримують дані прямим запитом. Пошук невідомої назви Admin повертає
            порожній список без витоку даних іншої партії.
            """)
    public void batchInspectorIsAdminOnlyAndUnknownNameReturnsEmptyList() {
        String batchName = "WMS12-RBAC-" + System.currentTimeMillis();
        RelocationResponse received = relocationFixture.createExternalReceive(
                UserRole.ADMIN, owner1StorageId, anchorResourceId, 2.0, batchName);

        try {
            assertThat(inventoryFixture.inspectBatch(batchName, UserRole.ADMIN)).hasSize(1);

            Response locationOwnerResponse = inventoryFixture.inspectBatchRaw(
                    batchName, UserRole.DYNAMIC_LOCATION_OWNER);
            assertThat(locationOwnerResponse.statusCode()).isEqualTo(403);
            assertThat(locationOwnerResponse.body().asString()).doesNotContain(batchName);

            Response anonymousResponse = inventoryFixture.inspectBatchRaw(batchName, UserRole.ANONYMOUS);
            assertThat(anonymousResponse.statusCode()).isEqualTo(401);
            assertThat(anonymousResponse.body().asString()).doesNotContain(batchName);

            assertThat(inventoryFixture.inspectBatch(
                    "WMS12-NOT-FOUND-" + UUID.randomUUID(), UserRole.ADMIN)).isEmpty();
        } finally {
            deleteRelocationQuietly(received, owner1StorageId);
        }
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-012-003")
    @Story("Production date range and technological map")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Два виробничі записи однієї UUID-партії за різні дати повертаються в одному
            inspector result. Мінімальна й максимальна дати формують діапазон на UI,
            обидва записи посилаються на одну технологічну карту.
            """)
    public void productionBatchExposesDateRangeAndOneTechMap() {
        TechnologicalMapFixture.IsolatedTechMapContext isolated = null;
        List<ManufacturingItemResponse> productions = new ArrayList<>();
        Set<Long> createdResourceIds = new LinkedHashSet<>();

        try {
            techMapFixture.prepareContext();
            isolated = techMapFixture.createIsolatedProductionTechMap(
                    UserRole.ADMIN, owner1StorageId, "WMS12-PROD");
            TechnologicalMapResponse techMap = isolated.getTechMap();
            Long productId = isolated.getProduct().getId();
            createdResourceIds.add(productId);
            techMap.getInput().stream()
                    .filter(input -> input.getResource() != null)
                    .map(input -> input.getResource().getId())
                    .forEach(createdResourceIds::add);
            techMapFixture.seedStockForIsolatedTechMap(
                    productionFixture, owner1StorageId, techMap, 50.0);

            String batchName = ProductionDataFactory.uniqueBatchNumber();
            LocalDate from = LocalDate.now().minusDays(2);
            LocalDate to = LocalDate.now();
            productions.add(productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, techMap, 1.0, batchName, from));
            productions.add(productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, techMap, 2.0, batchName, to));

            BatchInspectionResponse inspection = requireInspection(batchName, productId);
            assertThat(inspection.getProductions()).hasSize(2);
            assertThat(inspection.getProductions())
                    .extracting(BatchInspectionResponse.Production::getDate)
                    .containsExactlyInAnyOrder(from, to);
            assertThat(inspection.getProductions())
                    .allSatisfy(production -> {
                        assertThat(production.getTechMap()).isNotNull();
                        assertThat(production.getTechMap().getId()).isEqualTo(techMap.getId());
                        assertThat(production.getTechMap().getName()).isEqualTo(techMap.getName());
                    });
            assertThat(inspection.getId()).isNotNull();
            assertThat(requireStorageBatch(owner1StorageId, productId, batchName).getBatchUuid())
                    .isEqualTo(inspection.getId());
        } finally {
            cleanupProductionScenario(productions, isolated, createdResourceIds);
        }
    }

    private BatchInspectionResponse requireInspection(String batchName, Long resourceId) {
        return inventoryFixture.inspectBatch(batchName, UserRole.ADMIN).stream()
                .filter(item -> item.getResource() != null
                        && Objects.equals(resourceId, item.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Batch Inspector result missing name=" + batchName + ", resourceId=" + resourceId));
    }

    private StorageItemBatchResponse requireStorageBatch(long storageId,
                                                         long resourceId,
                                                         String batchName) {
        return inventoryFixture.getBatchesByResource(storageId, resourceId, UserRole.ADMIN).stream()
                .filter(batch -> Objects.equals(batchName, batch.getBatchNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Storage batch missing storageId=" + storageId + ", resourceId=" + resourceId
                                + ", batch=" + batchName));
    }

    private static RelocationItemBatchResponse requireRelocationBatch(RelocationResponse relocation,
                                                                       String batchName) {
        return relocation.getItems().stream()
                .flatMap(item -> item.getBatches().stream())
                .filter(batch -> Objects.equals(batchName, batch.getBatchNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Relocation " + relocation.getId() + " missing batch " + batchName));
    }

    private static void assertStock(BatchInspectionResponse inspection,
                                    long storageId,
                                    double expectedAmount) {
        assertThat(inspection.getStock())
                .filteredOn(stock -> stock.getStorage() != null
                        && Objects.equals(storageId, stock.getStorage().getId()))
                .singleElement()
                .satisfies(stock -> assertThat(stock.getAmount().doubleValue())
                        .isCloseTo(expectedAmount, within(0.001)));
    }

    private void deleteRelocationQuietly(RelocationResponse relocation, long storageId) {
        if (relocation == null || relocation.getId() == null) {
            return;
        }
        try {
            relocationFixture.deleteRelocation(UserRole.ADMIN, relocation.getId(), storageId);
        } catch (RuntimeException e) {
            log.warn("Cleanup relocation {} failed: {}", relocation.getId(), e.getMessage());
        }
    }

    private void cleanupProductionScenario(List<ManufacturingItemResponse> productions,
                                           TechnologicalMapFixture.IsolatedTechMapContext isolated,
                                           Set<Long> resourceIds) {
        for (int i = productions.size() - 1; i >= 0; i--) {
            try {
                productionFixture.deleteAs(
                        UserRole.ADMIN, productions.get(i).getId(), owner1StorageId);
            } catch (RuntimeException e) {
                log.warn("Cleanup production {} failed: {}", productions.get(i).getId(), e.getMessage());
            }
        }
        for (Long resourceId : resourceIds) {
            try {
                inventoryFixture.removeResourceFromStorage(owner1StorageId, resourceId, UserRole.ADMIN);
            } catch (RuntimeException e) {
                log.warn("Cleanup storage resource {} failed: {}", resourceId, e.getMessage());
            }
        }
        if (isolated != null && isolated.getTechMap() != null) {
            try {
                techMapFixture.deactivateTechMap(
                        UserRole.ADMIN, isolated.getTechMap().getId(), owner1StorageId);
            } catch (RuntimeException e) {
                log.warn("Cleanup tech map {} failed: {}",
                        isolated.getTechMap().getId(), e.getMessage());
            }
        }
        for (Long resourceId : resourceIds) {
            try {
                resourceFixture.deactivate(UserRole.ADMIN, resourceId);
            } catch (RuntimeException e) {
                log.warn("Cleanup catalog resource {} failed: {}", resourceId, e.getMessage());
            }
        }
        try {
            techMapFixture.setMode(owner1StorageId, StorageTechnologicalMapMode.READ_ONLY);
        } catch (RuntimeException e) {
            log.warn("Restore READ_ONLY failed: {}", e.getMessage());
        }
    }
}
