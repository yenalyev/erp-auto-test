package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.StorageAlertsPage;
import com.erp.test_context.ContextKey;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertOwnerSaveUiTest extends BaseUITest {

    private AlertFixture alerts;
    private StorageFixture storages;
    private UserFixture users;
    private UserFixture.BusinessActor owner;
    private StorageResponse storage;
    private ResourceResponse resource;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupOwnerAndStorage() {
        alerts = new AlertFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        resources.prepareContext();
        List<ResourceResponse> available = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resource = available.getFirst();

        storage = storages.createUniqueStorage("alrt-ui-owner-");
        alerts.createOrUpdateStockAlert(UserRole.ADMIN, storage.getId(), resource.getId(), 1.0);
        owner = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupOwnerAndStorage() {
        try {
            if (alerts != null && storage != null) {
                alerts.deleteAlertForStorage(storage.getId(), UserRole.ADMIN);
            }
        } finally {
            if (users != null) {
                users.deactivateTrackedUsers();
            }
            if (storages != null) {
                storages.deactivateTrackedStorages(UserRole.ADMIN);
            }
        }
    }

    @Test
    @TestCaseId("TC-UI-ALERT-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Тимчасовий керівник власної локації натискає «Зберегти» на /alerts/{id}; запит завершується без 403.")
    public void locationOwnerCanSaveStockAlertFromPage() {
        injectSessionCookies(
                authService.getSessionForUser(owner.username(), owner.password()),
                sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storage.getId() + "');");

        StorageAlertsPage alertsPage = new StorageAlertsPage(page).open(storage.getId());
        assertThat(alertsPage.showsResource(resource.getName())).isTrue();

        Response save = page.waitForResponse(
                response -> response.url().contains("/api/v1/alerts")
                        && ("POST".equals(response.request().method())
                            || "PUT".equals(response.request().method())),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Зберегти")).click());
        assertThat(save.status())
                .as("Save alert from owner UI, %s %s", save.request().method(), save.url())
                .isBetween(200, 299);
        assertThat(page.getByText("Не вдалося зберегти сповіщення").count()).isZero();
        alertsPage.attachScreenshot("TC-UI-ALERT-004 — saved by location owner");
    }
}
