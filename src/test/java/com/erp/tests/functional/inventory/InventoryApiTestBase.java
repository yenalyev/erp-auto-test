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

@Slf4j
abstract class InventoryApiTestBase extends BaseFunctionalTest {

    protected InventoryFixture inventoryFixture;
    protected RelocationFixture relocationFixture;
    protected long owner1StorageId;
    protected long owner2StorageId;
    protected Long anchorResourceId;

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

        relocationFixture.ensureStock(owner1StorageId, anchorResourceId, 50.0);
        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    public void resetInventorySession() {
        inventoryFixture.ensureClosed(owner1StorageId);
    }

    @AfterMethod(alwaysRun = true)
    public void teardownInventorySession() {
        inventoryFixture.ensureClosed(owner1StorageId);
    }
}
