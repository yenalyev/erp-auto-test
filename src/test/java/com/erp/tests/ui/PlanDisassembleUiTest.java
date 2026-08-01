package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.PlanExecutionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Plan-execution «Розбір» block after creating a disassemble batch (TC-PLN-001 / TC-PLN-002).
 * API GET /disassemble is the same source the UI aggregates in {@code getDailyDisassembled}.
 */
@Slf4j
@Epic("Plans")
@Feature("Plan Execution — Розбір")
public class PlanDisassembleUiTest extends BaseUITest {

    private DisassembleFixture disassembleFixture;
    private long storageId;
    private TechnologicalMapResponse techMap;
    private long inputResourceId;
    private long outputResourceId;
    private String inputResourceName;
    private String outputResourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        disassembleFixture = new DisassembleFixture(testContext, apiExecutor);
        disassembleFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        techMap = disassembleFixture.techMap();
        inputResourceId = disassembleFixture.inputResourceId();
        outputResourceId = disassembleFixture.outputResourceId();
        inputResourceName = disassembleFixture.inputResourceName();
        outputResourceName = disassembleFixture.outputResourceName();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PLN-001")
    @Story("New disassemble batch increases «Розбір» amounts on plan-execution")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: зафіксувати baseline блоку «Розбір» (API GET /disassemble за сьогодні).
            Act: створити розбір (кількість = коефіцієнт техкарти для output).
            Assert: сума розібраного input і отриманого output зростає на величину партії;
            на UI /plan-execution у блоці «Розбір» видно ті самі денні суми.""")
    public void disassembleIncreasesPlanExecutionRozbir() {
        double disassembleAmount = 3.0;
        double outputCoef = techMap.getOutput().getFirst().getAmount();
        double expectedOutput = disassembleAmount * outputCoef;
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();

        Allure.parameter("disassembleAmount", disassembleAmount);
        Allure.parameter("expectedOutput", expectedOutput);

        double baselineInput = Allure.step("Baseline розібраного input", () ->
                disassembleFixture.getTodayDisassembledAmount(storageId, UserRole.OWNER_1, inputResourceId));
        double baselineOutput = Allure.step("Baseline отриманого output", () ->
                disassembleFixture.getTodayDisassembleOutputAmount(storageId, UserRole.OWNER_1, outputResourceId));

        Allure.step("Створити розбір через API", () ->
                disassembleFixture.createAs(
                        UserRole.OWNER_1,
                        storageId,
                        techMap,
                        disassembleAmount,
                        expectedOutput,
                        batchNumber));

        double afterInput = Allure.step("API: розібрано після створення", () ->
                disassembleFixture.getTodayDisassembledAmount(storageId, UserRole.OWNER_1, inputResourceId));
        double afterOutput = Allure.step("API: отримано після створення", () ->
                disassembleFixture.getTodayDisassembleOutputAmount(storageId, UserRole.OWNER_1, outputResourceId));

        assertThat(afterInput - baselineInput)
                .as("Розібрано (input) має зрости на %s", disassembleAmount)
                .isCloseTo(disassembleAmount, within(0.001));
        assertThat(afterOutput - baselineOutput)
                .as("Отримано (output) має зрости на %s", expectedOutput)
                .isCloseTo(expectedOutput, within(0.001));

        Allure.step("UI: перевірити блок «Розбір» на /plan-execution", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            PlanExecutionPage planPage = new PlanExecutionPage(page).open().waitForDisassembleSection();

            assertThat(planPage.isDisassembleSectionVisible()).isTrue();
            assertThat(planPage.getDisassembleDayInputAmount(inputResourceName))
                    .as("UI «Розібрано за день» для %s", inputResourceName)
                    .isCloseTo(afterInput, within(0.001));
            assertThat(planPage.getDisassembleDayOutputAmount(outputResourceName))
                    .as("UI «Отримано за день» для %s", outputResourceName)
                    .isCloseTo(afterOutput, within(0.001));

            planPage.attachScreenshot("TC-PLN-001 — Розбір after disassemble");
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLN-002")
    @Story("«Розбір» shows actual output totalAmount, not tech-map coefficient")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: baseline «Отримано» для output-ресурсу.
            Act: створити розбір з «Усього», відмінним від розрахунку за техкартою.
            Assert: дельта на API і в UI блоці «Розбір» дорівнює фактичному «Усього».""")
    public void disassembleActualOutputShownOnPlanExecution() {
        double disassembleAmount = 4.0;
        double outputCoef = techMap.getOutput().getFirst().getAmount();
        double techMapExpected = disassembleAmount * outputCoef;
        double actualTotalProduced = techMapExpected + 1.5;
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();

        Allure.parameter("techMapExpected", techMapExpected);
        Allure.parameter("actualTotalProduced", actualTotalProduced);

        assertThat(actualTotalProduced)
                .as("Тест має використовувати «Усього» ≠ нормі техкарти")
                .isNotEqualTo(techMapExpected);

        double baselineOutput = Allure.step("Baseline отриманого output", () ->
                disassembleFixture.getTodayDisassembleOutputAmount(storageId, UserRole.OWNER_1, outputResourceId));

        Allure.step("Створити розбір з фактичним «Усього»", () ->
                disassembleFixture.createAs(
                        UserRole.OWNER_1,
                        storageId,
                        techMap,
                        disassembleAmount,
                        actualTotalProduced,
                        batchNumber));

        double afterOutput = Allure.step("API: отримано після створення", () ->
                disassembleFixture.getTodayDisassembleOutputAmount(storageId, UserRole.OWNER_1, outputResourceId));

        assertThat(afterOutput - baselineOutput)
                .as("Отримано має зрости на фактичне «Усього» (%s), не на норму (%s)",
                        actualTotalProduced, techMapExpected)
                .isCloseTo(actualTotalProduced, within(0.001));

        Allure.step("UI: перевірити «Отримано» в блоці «Розбір»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            PlanExecutionPage planPage = new PlanExecutionPage(page).open().waitForDisassembleSection();

            assertThat(planPage.getDisassembleDayOutputAmount(outputResourceName))
                    .as("UI «Отримано за день» має відповідати факту, не техкарті")
                    .isCloseTo(afterOutput, within(0.001));

            planPage.attachScreenshot("TC-PLN-002 — Розбір actual output");
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
