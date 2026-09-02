package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UI regression for operation history after NSP create and disassemble with custom output totals.
 */
@Slf4j
@Epic("Operation History")
@Feature("NSP and disassemble in history UI")
public class OperationHistoryUiTest extends BaseUITest {

    private static final String PRODUCED_CARD_TITLE = "Вироблено";
    private static final String USED_CARD_TITLE = "Використано";

    private DisassembleFixture disassembleFixture;
    private NonSeriesProductionFixture nspFixture;
    private ResourceFixture resourceFixture;
    private long storageId;
    private TechnologicalMapResponse techMap;
    private long outputResourceId;
    private String outputResourceName;

    private final List<Long> createdNspIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        disassembleFixture = new DisassembleFixture(testContext, apiExecutor);
        disassembleFixture.prepareContext();
        nspFixture = new NonSeriesProductionFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        storageId = ConfigProvider.getOwner1StorageId();
        techMap = disassembleFixture.techMap();
        outputResourceId = disassembleFixture.outputResourceId();
        outputResourceName = disassembleFixture.outputResourceName();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedNsp() {
        for (Long id : createdNspIds) {
            try {
                nspFixture.deleteAs(UserRole.OWNER_1, id, storageId);
            } catch (Exception e) {
                log.warn("Failed to delete NSP {}: {}", id, e.getMessage());
            }
        }
        createdNspIds.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-OPER-HIST-001")
    @Story("NSP qty>1: «Використано» = qty × per-unit")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner створює несерійне виробництво з кількістю > 1.
            У «Історія операцій» картка «Використано» збільшується на qty × витрати на одиницю,
            а не на витрати однієї одиниці.""")
    public void nspQtyGreaterThanOneUsesTotalInHistory() {
        int productAmount = 10;
        double perUnit = 2.0;
        assertNspUsedDelta(productAmount, perUnit, NonSeriesProductionStatus.IN_PROGRESS,
                "TC-OPER-HIST-001 — used = qty × per-unit");
    }

    @Test(priority = 20)
    @TestCaseId("TC-OPER-HIST-002")
    @Story("NSP IN_PROGRESS usage appears in operation history")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner створює несерійне виробництво зі статусом «В процесі».
            У «Історія операцій» відображаються витрати використаних ресурсів.""")
    public void nspInProgressUsageShownInHistory() {
        assertNspUsedDelta(3, 2.0, NonSeriesProductionStatus.IN_PROGRESS,
                "TC-OPER-HIST-002 — NSP IN_PROGRESS used");
    }

    @Test(priority = 30)
    @TestCaseId("TC-OPER-HIST-003")
    @Story("NSP DONE usage appears in operation history")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner створює несерійне виробництво зі статусом «Завершено».
            У «Історія операцій» відображаються витрати використаних ресурсів.""")
    public void nspDoneUsageShownInHistory() {
        assertNspUsedDelta(3, 2.0, NonSeriesProductionStatus.DONE,
                "TC-OPER-HIST-003 — NSP DONE used");
    }

    @Test(priority = 40)
    @TestCaseId({
            "TC-OPER-HIST-004",
            "TC-UI-HIST-DIS-001"
    })
    @Story("Disassemble actual produced amount in «Вироблено» summary")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Після створення розбору (setup через API) з полем «Усього», що відрізняється від кількості
            за техкартою, Owner 1 відкриває «Історія операцій» (/history) і перевіряє картку «Вироблено».
            Очікується: сумарна кількість отриманого ресурсу збільшується на фактичне значення «Усього»,
            а не на розрахунок за коефіцієнтом техкарти.
            """)
    public void disassembleActualProducedAmountShownInHistory() {
        double disassembleAmount = 4.0;
        double outputCoef = techMap.getOutput().getFirst().getAmount();
        double techMapExpectedProduced = disassembleAmount * outputCoef;
        double actualTotalProduced = techMapExpectedProduced + 1.0;
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();

        Allure.parameter("storageId", storageId);
        Allure.parameter("disassembleAmount", disassembleAmount);
        Allure.parameter("techMapOutputCoef", outputCoef);
        Allure.parameter("techMapExpectedProduced", techMapExpectedProduced);
        Allure.parameter("actualTotalProduced", actualTotalProduced);
        Allure.parameter("outputResource", outputResourceName);

        assertThat(actualTotalProduced)
                .as("Тест має використовувати «Усього», відмінне від значення за техкартою")
                .isNotEqualTo(techMapExpectedProduced);

        double baselineProduced = Allure.step("Зафіксувати baseline «Вироблено» через API", () ->
                disassembleFixture.getProducedSummaryAmount(storageId, UserRole.OWNER_1, outputResourceId));

        Allure.step("Створити розбір з фактичним «Усього» через API", () ->
                disassembleFixture.createAs(
                        UserRole.OWNER_1,
                        storageId,
                        techMap,
                        disassembleAmount,
                        actualTotalProduced,
                        batchNumber));

        Allure.step("Перевірити картку «Вироблено» на UI", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);

            OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
            assertThat(history.isLoaded()).isTrue();
            assertThat(history.isProducedSummaryVisible())
                    .as("Картка «Вироблено» має бути видимою")
                    .isTrue();

            double uiProducedTotal = history.getSummaryCardAmountForResource(PRODUCED_CARD_TITLE, outputResourceName);
            double uiDelta = uiProducedTotal - baselineProduced;

            Allure.parameter("baselineProduced", baselineProduced);
            Allure.parameter("uiProducedTotal", uiProducedTotal);
            Allure.parameter("uiDelta", uiDelta);

            assertThat(uiDelta)
                    .as("UI «Вироблено» має збільшитися на фактичне «Усього» (%s), а не на значення за техкартою (%s)",
                            actualTotalProduced, techMapExpectedProduced)
                    .isEqualTo(actualTotalProduced);

            history.attachScreenshot("TC-OPER-HIST-004 — produced summary after disassemble");
        });
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-HIST-NSP-001")
    @Story("NSP used resource appears in history table, not only in «Використано» card")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після створення несерійного виробництва Owner 1 відкриває «Історія операцій» (/history).
            Очікувана поведінка (REQ-OPER-HIST AC-15): ресурс є в картці «Використано»
            і в таблиці рядком з badge «Використано»; після чекбокса картки таблиця не порожня.

            Відомий дефект: GET resource-operation-history заповнює totalUsedResources (картка),
            але operationHistoryList фільтрує NSP за operationSource — рядок USED відсутній,
            після фільтра картки показується «Немає даних за вибраний період».
            """)
    public void nspUsedResourceAppearsInHistoryTable() {
        int productAmount = 2;
        double perUnit = 1.0;
        double expectedUsed = productAmount * perUnit;
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        ResourceResponse resource = Allure.step("Створити ізольований ресурс для NSP", () ->
                resourceFixture.createUniqueResource("OH-NSP-TBL"));
        long resourceId = resource.getId();
        String resourceName = resource.getName().trim();

        Allure.parameter("resourceName", resourceName);
        Allure.parameter("productAmount", productAmount);
        Allure.parameter("perUnit", perUnit);
        Allure.parameter("expectedUsed", expectedUsed);

        Allure.step("Засіяти залишок " + (expectedUsed + 10), () ->
                RelocationStockSeeder.receiveFromSupplier(
                        apiExecutor,
                        UserRole.OWNER_1,
                        storageId,
                        Map.of(resourceId, expectedUsed + 10)));

        NonSeriesProductionResponse created = Allure.step("Створити NSP IN_PROGRESS", () ->
                nspFixture.createAs(
                        UserRole.OWNER_1,
                        NonSeriesProductionStatus.IN_PROGRESS,
                        product,
                        productAmount,
                        resourceId,
                        perUnit));
        createdNspIds.add(created.getId());

        injectRoleSession(UserRole.OWNER_1, storageId);

        Allure.step("UI: картка «Використано» і рядок у таблиці", () -> {
            OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
            assertThat(history.isLoaded()).isTrue();
            assertThat(history.isSummaryCardVisible(USED_CARD_TITLE))
                    .as("Картка «Використано» має бути видимою")
                    .isTrue();

            double uiUsed = history.getSummaryCardAmountForResource(USED_CARD_TITLE, resourceName);
            assertThat(uiUsed)
                    .as("Картка «Використано» має містити ресурс %s з кількістю %s",
                            resourceName, expectedUsed)
                    .isCloseTo(expectedUsed, within(0.001));

            history.attachScreenshot("TC-UI-HIST-NSP-001 — card filled, table before filter");

            assertThat(history.tableHasResourceOperation(resourceName, USED_CARD_TITLE))
                    .as("Таблиця має містити рядок «%s» з операцією «Використано» (не лише картка)",
                            resourceName)
                    .isTrue();

            history.filterBySummaryCard(USED_CARD_TITLE, resourceName);
            history.attachScreenshot("TC-UI-HIST-NSP-001 — after Використано filter");

            assertThat(history.isResourceTableEmptyStateVisible())
                    .as("Після фільтра картки «Використано» таблиця не має бути порожньою")
                    .isFalse();
            assertThat(history.tableHasResourceOperation(resourceName, USED_CARD_TITLE))
                    .as("Після фільтра картки рядок «%s / Використано» лишається в таблиці",
                            resourceName)
                    .isTrue();
        });
    }

    /**
     * Uses a freshly created resource (no prior history noise) and UI↔UI baseline so the
     * history date window matches what the page actually aggregates.
     */
    private void assertNspUsedDelta(int productAmount,
                                    double perUnit,
                                    NonSeriesProductionStatus status,
                                    String screenshotLabel) {
        double expectedUsed = productAmount * perUnit;
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        ResourceResponse resource = Allure.step("Створити ізольований ресурс для NSP", () ->
                resourceFixture.createUniqueResource("OH-NSP"));
        long resourceId = resource.getId();
        String resourceName = resource.getName().trim();

        Allure.step("Засіяти залишок " + (expectedUsed + 10), () ->
                RelocationStockSeeder.receiveFromSupplier(
                        apiExecutor,
                        UserRole.OWNER_1,
                        storageId,
                        Map.of(resourceId, expectedUsed + 10)));

        injectRoleSession(UserRole.OWNER_1, storageId);
        double baselineUsed = Allure.step("UI baseline «Використано»", () -> {
            OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
            return history.getSummaryCardAmountForResourceOrZero(USED_CARD_TITLE, resourceName);
        });

        NonSeriesProductionResponse created = Allure.step("Створити NSP status=" + status, () ->
                nspFixture.createAs(
                        UserRole.OWNER_1,
                        status,
                        product,
                        productAmount,
                        resourceId,
                        perUnit));
        createdNspIds.add(created.getId());

        Allure.step("UI: «Використано» зросло на qty × per-unit", () -> {
            OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
            assertThat(history.isSummaryCardVisible(USED_CARD_TITLE)).isTrue();

            double uiUsed = history.getSummaryCardAmountForResource(USED_CARD_TITLE, resourceName);
            assertThat(uiUsed - baselineUsed)
                    .as("«Використано» для NSP %s має зрости на %s (= %s × %s)",
                            status, expectedUsed, productAmount, perUnit)
                    .isCloseTo(expectedUsed, within(0.001));

            history.attachScreenshot(screenshotLabel);
        });
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
