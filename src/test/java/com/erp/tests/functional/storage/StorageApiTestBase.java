package com.erp.tests.functional.storage;

import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
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
    protected StorageRegionFixture regionFixture;

    @BeforeClass(alwaysRun = true)
    public void setupStorageApiBase() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    @Step("Cleanup: архівація тестових локацій після методу")
    public void cleanupCreatedStoragesAfterTest() {
        cleanupTestArtifacts();
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: архівація тестових локацій після класу")
    public void cleanupCreatedStoragesAfterClass() {
        cleanupTestArtifacts();
    }

    private void cleanupTestArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }
}
