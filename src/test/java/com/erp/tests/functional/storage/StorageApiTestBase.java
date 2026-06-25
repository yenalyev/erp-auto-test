package com.erp.tests.functional.storage;

import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

/**
 * Базовий клас API-тестів локацій.
 * <p>Усі локації, створені через {@link StorageFixture#createStorage} та похідні методи,
 * потрапляють у cleanup-чергу і архівуються (DELETE deactivate) після кожного тесту та після класу.
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
    @Step("Cleanup: архівація тестових локацій після методу")
    public void cleanupCreatedStoragesAfterTest() {
        archiveTrackedStorages();
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: архівація тестових локацій після класу")
    public void cleanupCreatedStoragesAfterClass() {
        archiveTrackedStorages();
    }

    private void archiveTrackedStorages() {
        if ("staging".equals(System.getProperty("env", "debug"))) {
            log.warn("Staging mode — skipping automatic storage cleanup");
            storageFixture.clearTrackedStorages();
            return;
        }
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }
}
