package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI regression for edit Tab 1 (add output) → Tab 2 decompose after a fully generated plan.
 * API counterpart: {@code GlobalPlanEditAddOutputTest} (TC-GP-050).
 */
@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanEditAddOutputUiTest extends BaseUITest {

    private static final double OUTPUT_A_AMOUNT = 10.0;
    private static final String OUTPUT_B_AMOUNT = "5";

    private GlobalPlanFixture globalPlanFixture;
    private GlobalPlanChainContext chain;
    private String resourceAName;
    private String resourceBName;
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();
    private final List<Long> generatedPlanIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        chain = globalPlanFixture.prepareDecompositionChain();

        ResourceResponse resourceA = chain.getResourceA();
        ResourceResponse resourceB = chain.getResourceB();
        resourceAName = resourceA.getName();
        resourceBName = resourceB.getName();

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectAllLocationsView();

        log.info("Global plan edit-add-output UI setup — A={}, B={}", resourceAName, resourceBName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCreatedPlans() {
        if (globalPlanFixture == null) {
            return;
        }
        globalPlanFixture.cleanupGeneratedPlans(generatedPlanIdsToCleanup);
        for (Long planId : globalPlanIdsToCleanup) {
            try {
                globalPlanFixture.deleteGlobalPlan(planId);
                log.info("Cleaned up global plan id={}", planId);
            } catch (AssertionError e) {
                log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-UI-050")
    @Story("Decompose after adding output item on edit (UI)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після повного створення глобального плану (етапи 1–4) редагування етапу 1
            з додаванням ще одного пункту output має відкривати етап 2 без помилки.
            
            **UI-шлях:** `useGlobalPlanDecomposition.start()` після «Зберегти зміни»
            (`onSaved → Tab 2`) — block 0 = поточні `plan.output`, auto-assign для нового output.
            
            **Підготовка (API):** ланцюг M1/M2/M3 з одним виходом на PRODUCTION-карту
            (M1: 2B+3x→1A, M2: 2y+1C→1B, M3: 1z→1C); план output A=10;
            повна декомпозиція (B=12+8, C=20) + generate.
            **Роль:** ADMIN (cookies через Playwright inject)
            
            **Кроки:**
            1. Відкрити `/global-plans/{id}` (edit).
            2. «Додати виріб» — ресурс B з ланцюга (напівфабрикат у snapshot), amount=5.
            3. «Зберегти зміни» — UI переходить на Tab 2 і викликає POST /decompose.
            
            **Очікування:** HTTP 200 на decompose; немає екрану «Не вдалося розрахувати декомпозицію»;
            на Рівні 1 видно A (10 од.) і B (5 од.) як direct output.
            API-аналог: TC-GP-050.
            
            **Артефакти:** скріншот після save/decompose.
            """)
    public void decomposeAfterAddingOutputItemSucceedsInUi() {
        final String testCaseId = "TC-GP-UI-050";

        GlobalPlanResponse created = Allure.step("Arrange (API): план A=10 + decompose + generate", () -> {
            GlobalPlanResponse plan = globalPlanFixture.createGlobalPlan(OUTPUT_A_AMOUNT);
            globalPlanIdsToCleanup.add(plan.getId());

            DecompositionRequest decomposition = globalPlanFixture.buildCompleteDecomposition();
            globalPlanFixture.decompose(plan.getId(), decomposition);
            GenerationResponse generation = globalPlanFixture.generate(plan.getId(), decomposition);
            generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .forEach(generatedPlanIdsToCleanup::add);

            Allure.parameter("globalPlanId", plan.getId());
            Allure.parameter("outputA", resourceAName);
            Allure.parameter("outputB", resourceBName);
            log.info("{}: arranged plan id={} with snapshot", testCaseId, plan.getId());
            return plan;
        });

        GlobalPlanWizardPage wizard = Allure.step("Act: відкрити план і додати output B=" + OUTPUT_B_AMOUNT, () -> {
            GlobalPlanWizardPage page = new GlobalPlanWizardPage(this.page).openById(created.getId());
            page.attachScreenshot(testCaseId + " — edit tab1 before add");
            page.addOutputProduct(resourceBName, OUTPUT_B_AMOUNT);
            page.attachScreenshot(testCaseId + " — edit tab1 with B");
            return page;
        });

        Allure.step("Assert: після «Зберегти зміни» Tab 2 (decompose) без 400", () -> {
            int decomposeStatus = wizard.submitSaveChangesAndAwaitDecomposition();
            wizard.attachScreenshot(testCaseId + " — tab2 after save");

            assertThat(decomposeStatus)
                    .as("Після додавання пункту на етапі 1 етап 2 (decompose) має відкритися без 400")
                    .isEqualTo(200);
            assertThat(wizard.isDecompositionFailedVisible())
                    .as("Не має показуватися «Не вдалося розрахувати декомпозицію»")
                    .isFalse();

            wizard.waitForDecompositionIdle();
            assertThat(wizard.isResourceVisibleInDecompositionLevel(resourceAName, 1))
                    .as("Рівень 1 має містити output-ресурс A («%s»)", resourceAName)
                    .isTrue();
            assertThat(wizard.isResourceVisibleInDecompositionLevel(resourceBName, 1))
                    .as("Рівень 1 має містити доданий output-ресурс B («%s»)", resourceBName)
                    .isTrue();
            assertThat(wizard.getDecompositionRequiredAmountText(resourceAName))
                    .as("Рівень 1 — кількість A")
                    .contains("10");
            assertThat(wizard.getDecompositionRequiredAmountText(resourceBName))
                    .as("Рівень 1 — кількість B")
                    .contains(OUTPUT_B_AMOUNT);
            wizard.attachScreenshot(testCaseId + " — tab2 level 1 resources A and B");
            log.info("{}: Tab 2 opened successfully after adding output B", testCaseId);
        });
    }
}
