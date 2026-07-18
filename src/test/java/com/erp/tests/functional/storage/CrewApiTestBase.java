package com.erp.tests.functional.storage;

import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import org.testng.annotations.BeforeClass;

/**
 * Базовий клас для API-тестів областей CREWS та видачі на екіпажі.
 */
public abstract class CrewApiTestBase extends StorageApiTestBase {

    protected CrewRegionFixture crewFixture;
    protected RelocationFixture relocationFixture;
    protected ResourceFixture resourceFixture;
    protected InventoryFixture inventoryFixture;
    protected Long owner1StorageId;
    protected Long owner2StorageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupStorageApiBase")
    @Step("Підготовка fixtures для crew-тестів")
    public void setupCrewApiBase() {
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        SchemaRegistry.logSchemaCoverage();
    }

    /** Re-login so JWT picks up freshly created CREWS regions (appendGrantedCrews). */
    protected void refreshRoleSessions(UserRole... roles) {
        for (UserRole role : roles) {
            authService.invalidateSession(role.getUsername(), role.getPassword());
        }
        apiExecutor.clearSessionCache();
    }
}
