package com.erp.tests.functional.alert;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ResourceAlertRequest;
import com.erp.models.request.StorageAlertRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAlertResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertOwnerSaveApiTest extends BaseFunctionalTest {

    private static final UserRole OWNER = UserRole.DYNAMIC_LOCATION_OWNER;

    private AlertFixture alerts;
    private StorageFixture storages;
    private UserFixture users;
    private StorageResponse storage;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupOwnerAndStorage() {
        alerts = new AlertFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        resources.prepareContext();
        List<ResourceResponse> available = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = available.getFirst().getId();

        storage = storages.createUniqueStorage("alrt-owner-");
        UserFixture.BusinessActor actor = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
        apiExecutor.setSessionForRole(OWNER, actor.username(), actor.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupOwnerAndStorage() {
        try {
            if (alerts != null && storage != null) {
                alerts.deleteAlertForStorage(storage.getId(), UserRole.ADMIN);
            }
        } finally {
            apiExecutor.restoreDefaultSessionForRole(OWNER);
            if (users != null) {
                users.deactivateTrackedUsers();
            }
            if (storages != null) {
                storages.deactivateTrackedStorages(UserRole.ADMIN);
            }
        }
    }

    @Test
    @TestCaseId("TC-ALERT-005")
    @Story("Location owner saves stock alerts")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Новий керівник власної тестової локації створює й оновлює поріг через API без 403.")
    public void locationOwnerCanCreateAndUpdateOwnStockAlert() {
        StorageAlertRequest create = request(BigDecimal.ONE);
        Response createdResponse = apiExecutor.execute(ApiEndpointDefinition.ALERT_POST_CREATE, OWNER, create);
        assertThat(createdResponse.statusCode())
                .as("Owner POST /api/v1/alerts for own storage %s", storage.getId())
                .isBetween(200, 299);

        StorageAlertResponse created = alerts.getByStorageId(storage.getId(), OWNER);
        assertThat(created).as("Owner reads created alert").isNotNull();
        assertThat(created.getId()).isNotNull();

        Response updatedResponse = apiExecutor.execute(
                ApiEndpointDefinition.ALERT_PUT_UPDATE, OWNER,
                request(BigDecimal.valueOf(2)), String.valueOf(created.getId()));
        assertThat(updatedResponse.statusCode())
                .as("Owner PUT /api/v1/alerts/%s", created.getId())
                .isBetween(200, 299);

        StorageAlertResponse updated = alerts.getByStorageId(storage.getId(), OWNER);
        assertThat(updated.getResourceAlerts())
                .anySatisfy(row -> {
                    assertThat(row.getResource().getId()).isEqualTo(resourceId);
                    assertThat(row.getValue()).isEqualByComparingTo("2");
                });
    }

    private StorageAlertRequest request(BigDecimal limit) {
        return StorageAlertRequest.builder()
                .storageId(storage.getId())
                .resourceAlerts(List.of(ResourceAlertRequest.builder()
                        .resourceId(resourceId)
                        .value(limit)
                        .build()))
                .build();
    }
}
