package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.pages.NonSeriesProductionFormPage;
import com.erp.pages.NonSeriesProductionListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for Non-Series Production (/non-series-production).
 */
@Slf4j
@Epic("Non-Series Production")
@Feature("Non-Series Production UI")
public class NonSeriesProductionUITest extends BaseUITest {

    /** Minimum stock for 2 units at >75% consumption (integer math). */
    private static final double MIN_STOCK_FOR_TWO_UNITS_TEST = 6.0;
    private static final double MIN_STOCK_USAGE_RATIO = 0.75;

    private NonSeriesProductionFixture fixture;
    private String resourceName;
    private double seededStock;
    private long storageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        fixture = new NonSeriesProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        resourceName = testContext.get(ContextKey.NON_SERIES_RESOURCE_NAME);
        resourceId = testContext.get(ContextKey.NON_SERIES_RESOURCE_ID);
        storageId = ConfigProvider.getOwner1StorageId();

        log.info("Injecting OWNER_1 session cookies into BrowserContext");
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());

        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];

        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("OWNER_1 session injected — domain: {}, storageId: {}", domain, storageId);
    }

    @BeforeMethod(alwaysRun = true)
    public void refreshStockSnapshot() {
        seededStock = fixture.getResourceStock(storageId, resourceId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-NSP-001")
    @Story("Create and complete non-series production")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 створює 1 од. несерійного виробництва в статусі «В роботі» з повною витратою сировини,
            перевіряє запис у журналі, змінює статус на «Завершено» і перевіряє оновлення.
            """)
    public void createAndCompleteNonSeriesProduction() {
        final String productName = NonSeriesProductionDataFactory.uniqueProductName();
        final double usagePerUnit = seededStock;
        final String trimmedResourceName = trimmedResourceName();

        log.info("TC-UI-NSP-001: product={}, resource={}, usagePerUnit={}, stock={}",
                productName, trimmedResourceName, usagePerUnit, seededStock);

        runCreateInProgressEditToDoneFlow(
                productName, 1, usagePerUnit, trimmedResourceName, "TC-UI-NSP-001");
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-NSP-002")
    @Story("Create and complete non-series production — two units")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 створює 2 од. несерійного виробництва в статусі «В роботі».
            Цілісна витрата сировини на одиницю: загальне списання > 75% запасу.
            Далі — перевірка журналу, зміна статусу на «Завершено» і фінальна верифікація.
            """)
    public void createTwoUnitsWithMajorStockConsumptionAndComplete() {
        final int productAmount = 2;
        final double availableStock = seededStock;

        assertThat(availableStock)
                .as("На складі має бути достатньо сировини для тесту (мін. %.0f)", MIN_STOCK_FOR_TWO_UNITS_TEST)
                .isGreaterThanOrEqualTo(MIN_STOCK_FOR_TWO_UNITS_TEST);

        final long usagePerUnit = NonSeriesProductionDataFactory.usagePerUnitForStockUsageAbove(
                availableStock, productAmount, MIN_STOCK_USAGE_RATIO);
        final String productName = NonSeriesProductionDataFactory.uniqueProductName();
        final String trimmedResourceName = trimmedResourceName();
        final long stockUnits = (long) Math.floor(availableStock);
        final long totalUsage = productAmount * usagePerUnit;

        assertThat(totalUsage)
                .as("Загальна витрата має бути більше 75%% запасу")
                .isGreaterThan((long) Math.floor(stockUnits * MIN_STOCK_USAGE_RATIO));
        assertThat(totalUsage)
                .as("Загальна витрата не повинна перевищувати запас")
                .isLessThanOrEqualTo(stockUnits);

        log.info("TC-UI-NSP-002: product={}, resource={}, units={}, usagePerUnit={}, stock={}, totalUsage={}",
                productName, trimmedResourceName, productAmount, usagePerUnit, availableStock, totalUsage);

        Allure.parameter("productAmount", productAmount);
        Allure.parameter("availableStock", availableStock);
        Allure.parameter("usagePerUnit", usagePerUnit);
        Allure.parameter("totalUsage", totalUsage);

        runCreateInProgressEditToDoneFlow(
                productName, productAmount, usagePerUnit, trimmedResourceName, "TC-UI-NSP-002");
    }

    private void runCreateInProgressEditToDoneFlow(String productName,
                                                   int productAmount,
                                                   double usagePerUnit,
                                                   String trimmedResourceName,
                                                   String testId) {
        NonSeriesProductionListPage listPage = new NonSeriesProductionListPage(page);

        Allure.step("Відкрити журнал несерійного виробництва", () -> {
            listPage.open();
            listPage.attachScreenshot(testId + " — list initial");
        });

        NonSeriesProductionFormPage createForm = Allure.step(
                "Створити виріб у статусі «В роботі» з використаною сировиною", () -> {
                    NonSeriesProductionFormPage form = listPage.clickNewItem();
                    form.fillProductName(productName)
                            .setProductAmount(productAmount)
                            .setWorkerQty(2)
                            .selectStatusInProgress()
                            .addResourceUsage(trimmedResourceName, usagePerUnit);
                    form.attachScreenshot(testId + " — create form before save");
                    return form;
                });

        final NonSeriesProductionListPage listAfterCreate = Allure.step("Зберегти новий виріб", () -> {
            NonSeriesProductionListPage saved = createForm.saveProduct();
            assertThat(saved.isOnListPage())
                    .as("Після створення має бути редирект на /non-series-production")
                    .isTrue();
            return saved;
        });

        Allure.step("Перевірити, що виріб створено зі статусом «В роботі»", () -> {
            listAfterCreate.filterByProduct(productName);
            listAfterCreate.attachScreenshot(testId + " — list after create");

            assertThat(listAfterCreate.isProductVisible(productName))
                    .as("Виріб '%s' має з'явитися в журналі", productName)
                    .isTrue();
            assertThat(listAfterCreate.isInProgressStatusVisibleForProduct(productName))
                    .as("Статус виробу '%s' має бути «В роботі»", productName)
                    .isTrue();
        });

        NonSeriesProductionFormPage editForm = Allure.step(
                "Відкрити несерійне виробництво для редагування (кнопка «Редагувати»)", () -> {
                    NonSeriesProductionFormPage form = listAfterCreate.clickEditForProduct(productName);
                    assertThat(form.isEditMode())
                            .as("Має відкритися форма редагування")
                            .isTrue();
                    return form;
                });

        Allure.step("Перевірити доступну кількість сировини у блоці «Використана сировина на одиницю»", () -> {
            double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
            double expectedAvailable = stockAfterCreate + usagePerUnit;
            editForm.assertResourceRowShowsAvailableQuantity(trimmedResourceName, expectedAvailable);
            editForm.attachScreenshot(testId + " — edit form resource availability");
            Allure.parameter("stockAfterCreate", stockAfterCreate);
            Allure.parameter("usagePerUnit", usagePerUnit);
            Allure.parameter("expectedAvailable", expectedAvailable);
        });

        Allure.step("Змінити статус на «Завершено»", () -> {
            editForm.selectStatusDone();
            editForm.attachScreenshot(testId + " — edit form before save");
        });

        final NonSeriesProductionListPage listAfterUpdate = Allure.step(
                "Натиснути кнопку «Зберегти виріб»", () -> {
                    NonSeriesProductionListPage saved = editForm.saveProduct();
                    assertThat(saved.isOnListPage())
                            .as("Після редагування має бути редирект на /non-series-production")
                            .isTrue();
                    return saved;
                });

        Allure.step("Перевірити, що статус змінився на «Завершено»", () -> {
            listAfterUpdate.filterByProduct(productName);
            listAfterUpdate.attachScreenshot(testId + " — list after update");

            assertThat(listAfterUpdate.isDoneStatusVisibleForProduct(productName))
                    .as("Статус виробу '%s' має бути «Завершено»", productName)
                    .isTrue();
        });

        Allure.parameter("User", UserRole.OWNER_1.getUsername());
        Allure.parameter("Product", productName);
        Allure.parameter("Resource", trimmedResourceName);
        Allure.parameter("URL", listAfterUpdate.currentUrl());

        log.info("{} PASSED — url: {}", testId, listAfterUpdate.currentUrl());
    }

    private String trimmedResourceName() {
        return resourceName == null ? "" : resourceName.trim();
    }
}
