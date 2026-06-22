package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI regression for operation history after disassemble with custom output totals.
 */
@Slf4j
@Epic("Operation History")
@Feature("Disassemble in history UI")
public class OperationHistoryUiTest extends BaseUITest {

    private static final String PRODUCED_CARD_TITLE = "Вироблено";

    private DisassembleFixture disassembleFixture;
    private long storageId;
    private TechnologicalMapResponse techMap;
    private long outputResourceId;
    private String outputResourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        disassembleFixture = new DisassembleFixture(testContext, apiExecutor);
        disassembleFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        techMap = disassembleFixture.techMap();
        outputResourceId = disassembleFixture.outputResourceId();
        outputResourceName = techMap.getOutput().getFirst().getResource().getName().trim();
    }

    @Test
    @TestCaseId("TC-UI-HIST-DIS-001")
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
            page = browserContext.newPage();

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

            history.attachScreenshot("TC-UI-HIST-DIS-001 — produced summary after disassemble");
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
