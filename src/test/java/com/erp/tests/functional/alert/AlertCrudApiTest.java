package com.erp.tests.functional.alert;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAlertResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("Stock alerts")
public class AlertCrudApiTest extends BaseFunctionalTest {

    private AlertFixture fixture;
    private AlertFixture.AlertSnapshot snapshot;
    private long storageId;
    private long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupAlertTests() {
        fixture = new AlertFixture(testContext, apiExecutor);
        new ResourceFixture(testContext, apiExecutor).prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
        snapshot = fixture.snapshotStorageAlert(storageId, UserRole.ADMIN);
    }

    @AfterClass(alwaysRun = true)
    public void restoreAlert() {
        if (fixture != null && snapshot != null) {
            fixture.restoreSnapshot(storageId, UserRole.ADMIN, snapshot);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-ALERT-001")
    @Story("Alert CRUD")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Створити/оновити/прочитати сповіщення по залишках, потім відновити snapshot.")
    public void createReadUpdateAlert() {
        StorageAlertResponse created = fixture.createOrUpdateStockAlert(
                UserRole.ADMIN, storageId, resourceId, 1.0);
        assertThat(created.getId()).isNotNull();
        StorageAlertResponse byStorage = fixture.getByStorageId(storageId, UserRole.ADMIN);
        assertThat(byStorage).isNotNull();
        assertThat(byStorage.getId()).isEqualTo(created.getId());
    }
}
