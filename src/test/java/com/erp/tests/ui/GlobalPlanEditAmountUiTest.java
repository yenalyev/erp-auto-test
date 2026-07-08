package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.common.GlobalPlanChainExpectations;
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

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanEditAmountUiTest extends BaseUITest {

    private GlobalPlanFixture globalPlanFixture;
    private GlobalPlanChainContext chain;
    private String resourceAName;
    private String resourceBName;
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();

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
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCreatedGlobalPlans() {
        for (Long planId : globalPlanIdsToCleanup) {
            try {
                globalPlanFixture.deleteGlobalPlan(planId);
            } catch (AssertionError e) {
                log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-UI-032")
    @Story("Edit output amount recalculates Tab 2")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** зміна output A з 10 на 15 на Tab 1 і збереження перераховує
            потребу в B на Tab 2 («Потрібно: 30»).
            
            **Підготовка (API):** глобальний план output A=10.
            **Кроки:** відкрити edit → змінити amount → «Зберегти зміни» → Tab 2.
            """)
    public void editOutputAmountRecalculatesDecompositionOnTab2() {
        final String testCaseId = "TC-GP-UI-032";

        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(GlobalPlanChainExpectations.OUTPUT_A);
        globalPlanIdsToCleanup.add(created.getId());
        Allure.parameter("globalPlanId", created.getId());

        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openById(created.getId());
        wizard.setOutputAmount(resourceAName, String.valueOf((int) GlobalPlanChainExpectations.OUTPUT_A_EDITED));
        wizard.attachScreenshot(testCaseId + " — tab1 amount changed");

        int decomposeStatus = wizard.submitSaveChangesAndAwaitDecomposition();
        assertThat(decomposeStatus).isEqualTo(200);
        assertThat(wizard.isDecompositionFailedVisible()).isFalse();

        wizard.waitForDecompositionIdle();
        wizard.expandDecompositionForResource(resourceBName);

        String bRequired = wizard.getDecompositionRequiredAmountText(resourceBName);
        assertThat(bRequired)
                .as("Після зміни A=15 потреба в B має бути 30 (M1: 2B на 1A)")
                .contains("30");

        wizard.attachScreenshot(testCaseId + " — tab2 B requirement 30");
        log.info("{}: Tab 2 shows B required=30 after A edit", testCaseId);
    }
}
