package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.TechnologicalMapResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-011 — Batch technological map API contract")
public class InventoryBatchTechMapApiTest extends InventoryApiTestBase {

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    public void setupBatchTechMapFixtures() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-011-001")
    @Story("Batch response resolves its producing tech map")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /api/v1/storage-items/batches?storageId=&resourceId= повертає techmap{id,name}:
            окремі партії двох техкарт посилаються на відповідні карти, зовнішня партія має null.
            Якщо однаковий номер партії виготовляли за двома техкартами, повертається техкарта
            останнього за часом створення виробництва.
            """)
    public void batchesExposeTechMapAndChooseLatestProductionForAmbiguousBatch() {
        TechnologicalMapFixture.IsolatedTechMapContext isolated = null;
        TechnologicalMapResponse mapB = null;
        List<ManufacturingItemResponse> productions = new ArrayList<>();
        Set<Long> createdResourceIds = new LinkedHashSet<>();
        Long legacyProductionId = null;
        UUID originalLegacyBatchId = null;

        try {
            isolated = techMapFixture.createIsolatedProductionTechMap(
                    UserRole.ADMIN, owner1StorageId, "WMS11-API-A");
            TechnologicalMapResponse mapA = isolated.getTechMap();
            mapB = techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, mapA);
            createdResourceIds.add(isolated.getProduct().getId());
            mapA.getInput().stream()
                    .filter(input -> input.getResource() != null)
                    .map(input -> input.getResource().getId())
                    .forEach(createdResourceIds::add);

            assertThat(mapA.getId())
                    .as("Друга техкарта створена пізніше та має більший id")
                    .isLessThan(mapB.getId());
            techMapFixture.seedStockForIsolatedTechMap(
                    productionFixture, owner1StorageId, mapA, 50.0);

            String batchA = ProductionDataFactory.uniqueBatchNumber();
            String batchB = ProductionDataFactory.uniqueBatchNumber();
            String ambiguousBatch = ProductionDataFactory.uniqueBatchNumber();
            String legacyDonorBatch = ProductionDataFactory.uniqueBatchNumber();
            String noMapBatch = ProductionDataFactory.uniqueBatchNumber();

            productions.add(productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, mapA, 2.0, batchA));
            productions.add(productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, mapB, 3.0, batchB));
            ManufacturingItemResponse legacyA = productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, mapA, 1.0, ambiguousBatch);
            productions.add(legacyA);
            ManufacturingItemResponse legacyB = productionFixture.createAs(
                    UserRole.ADMIN, owner1StorageId, mapB, 1.0, legacyDonorBatch);
            productions.add(legacyB);
            legacyProductionId = legacyB.getId();
            originalLegacyBatchId = rebindProductionBatch(
                    legacyB.getId(), legacyA.getId());
            relocationFixture.createExternalReceive(
                    UserRole.ADMIN, owner1StorageId, isolated.getProduct().getId(), 4.0, noMapBatch);

            List<StorageItemBatchResponse> batches = inventoryFixture.getBatchesByResource(
                    owner1StorageId, isolated.getProduct().getId(), UserRole.OWNER_1);

            assertBatchTechMap(batches, batchA, mapA);
            assertBatchTechMap(batches, batchB, mapB);
            assertThat(findBatches(batches, noMapBatch))
                    .as("Партія без виробництва є у відповіді")
                    .isNotEmpty()
                    .allSatisfy(batch -> assertThat(batch.getTechmap())
                            .as("Зовнішня партія не має техкарти")
                            .isNull());
            assertBatchTechMap(batches, ambiguousBatch, mapB);
        } finally {
            restoreProductionBatch(legacyProductionId, originalLegacyBatchId);
            cleanupScenario(productions, isolated, mapB, createdResourceIds);
        }
    }

    @Step("DB: змоделювати legacy-партію з двома техкартами")
    private UUID rebindProductionBatch(Long productionId, Long sourceProductionId) {
        if (ensureDatabaseHelper() == null) {
            throw new SkipException(
                    "TC-WMS-011-001 legacy batch setup requires JDBC (-Duse.database=true)");
        }
        try {
            UUID originalBatchId = productionBatchId(productionId);
            UUID sharedBatchId = productionBatchId(sourceProductionId);
            try (PreparedStatement update = getDbHelper().getConnection().prepareStatement(
                    "UPDATE production_process SET batch_id = ? WHERE id = ?")) {
                update.setObject(1, sharedBatchId);
                update.setLong(2, productionId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            return originalBatchId;
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare legacy ambiguous production batch", e);
        }
    }

    private UUID productionBatchId(Long productionId) throws Exception {
        try (PreparedStatement query = getDbHelper().getConnection().prepareStatement(
                "SELECT batch_id FROM production_process WHERE id = ?")) {
            query.setLong(1, productionId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("Production process not found: " + productionId);
                }
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private void restoreProductionBatch(Long productionId, UUID originalBatchId) {
        if (productionId == null || originalBatchId == null || getDbHelper() == null) {
            return;
        }
        try (PreparedStatement update = getDbHelper().getConnection().prepareStatement(
                "UPDATE production_process SET batch_id = ? WHERE id = ?")) {
            update.setObject(1, originalBatchId);
            update.setLong(2, productionId);
            update.executeUpdate();
        } catch (Exception e) {
            log.warn("Could not restore batch for production {}: {}", productionId, e.getMessage());
        }
    }

    private static void assertBatchTechMap(List<StorageItemBatchResponse> batches,
                                           String batchNumber,
                                           TechnologicalMapResponse expected) {
        assertThat(findBatches(batches, batchNumber))
                .as("Партія %s є у відповіді", batchNumber)
                .isNotEmpty()
                .allSatisfy(batch -> {
                    assertThat(batch.getTechmap()).isNotNull();
                    assertThat(batch.getTechmap().getId()).isEqualTo(expected.getId());
                    assertThat(batch.getTechmap().getName()).isEqualTo(expected.getName());
                });
    }

    private static List<StorageItemBatchResponse> findBatches(List<StorageItemBatchResponse> batches,
                                                               String batchNumber) {
        return batches.stream()
                .filter(batch -> Objects.equals(batchNumber, batch.getBatchNumber()))
                .toList();
    }

    private void cleanupScenario(List<ManufacturingItemResponse> productions,
                                 TechnologicalMapFixture.IsolatedTechMapContext isolated,
                                 TechnologicalMapResponse mapB,
                                 Set<Long> createdResourceIds) {
        for (int i = productions.size() - 1; i >= 0; i--) {
            try {
                productionFixture.deleteAs(UserRole.ADMIN, productions.get(i).getId(), owner1StorageId);
            } catch (RuntimeException e) {
                log.warn("Cleanup production {} failed: {}", productions.get(i).getId(), e.getMessage());
            }
        }
        for (Long resourceId : createdResourceIds) {
            try {
                inventoryFixture.removeResourceFromStorage(owner1StorageId, resourceId, UserRole.ADMIN);
            } catch (RuntimeException e) {
                log.warn("Cleanup stock resource {} failed: {}", resourceId, e.getMessage());
            }
        }
        if (mapB != null) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, mapB.getId(), owner1StorageId);
            } catch (RuntimeException e) {
                log.warn("Cleanup tech map {} failed: {}", mapB.getId(), e.getMessage());
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
        for (Long resourceId : createdResourceIds) {
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
