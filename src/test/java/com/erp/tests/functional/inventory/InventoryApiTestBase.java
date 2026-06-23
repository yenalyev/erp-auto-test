package com.erp.tests.functional.inventory;

import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.ArrayList;
import java.util.List;

@Slf4j
abstract class InventoryApiTestBase extends BaseFunctionalTest {

    private static final double ANCHOR_STOCK_TARGET = 50.0;

    protected InventoryFixture inventoryFixture;
    protected RelocationFixture relocationFixture;
    protected long owner1StorageId;
    protected long owner2StorageId;
    protected Long anchorResourceId;
    protected final List<Long> resourcesToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void setupInventoryApiSuite() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        inventoryFixture.prepareContext();

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        anchorResourceId = testContext.get(
                com.erp.test_context.ContextKey.RELOCATION_RESOURCE_ID);

        relocationFixture.ensureStock(owner1StorageId, anchorResourceId, ANCHOR_STOCK_TARGET);
        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    public void resetInventorySession() {
        resourcesToCleanup.clear();
        inventoryFixture.ensureClosed(owner1StorageId);
        relocationFixture.ensureStock(owner1StorageId, anchorResourceId, ANCHOR_STOCK_TARGET);
    }

    @AfterMethod(alwaysRun = true)
    public void teardownInventorySession() {
        cleanupTrackedStorageResources();
        inventoryFixture.ensureClosed(owner1StorageId);
        relocationFixture.ensureStock(owner1StorageId, anchorResourceId, ANCHOR_STOCK_TARGET);
    }

    protected void trackStorageResourceForCleanup(Long resourceId) {
        if (resourceId != null && !resourcesToCleanup.contains(resourceId)) {
            resourcesToCleanup.add(resourceId);
        }
    }

    private void cleanupTrackedStorageResources() {
        for (Long resourceId : resourcesToCleanup) {
            if (anchorResourceId.equals(resourceId)) {
                continue;
            }
            try {
                inventoryFixture.removeResourceFromStorage(
                        owner1StorageId, resourceId, com.erp.enums.UserRole.ADMIN);
            } catch (Exception e) {
                log.warn("Inventory cleanup failed for resource {}: {}", resourceId, e.getMessage());
            }
        }
        resourcesToCleanup.clear();
    }
}
