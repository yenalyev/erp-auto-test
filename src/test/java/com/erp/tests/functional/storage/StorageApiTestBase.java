package com.erp.tests.functional.storage;

import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

/**
 * Базовий клас API-тестів локацій: після кожного тесту деактивує створені під час тесту записи.
 */
@Slf4j
public abstract class StorageApiTestBase extends BaseFunctionalTest {

    protected StorageFixture storageFixture;

    @BeforeClass(alwaysRun = true)
    public void setupStorageApiBase() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    @Step("Cleanup: деактивація тестових локацій")
    public void cleanupCreatedStorages() {
        if ("staging".equals(System.getProperty("env", "debug"))) {
            log.warn("Staging mode — skipping automatic storage cleanup");
            storageFixture.clearTrackedStorages();
            return;
        }
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }
}
