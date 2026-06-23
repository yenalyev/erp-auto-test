package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.GlobalPlansPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path UI flow: create global plan through all 4 wizard tabs and generate per-location plans.
 */
@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanCreateUiTest extends BaseUITest {

    private static final double OUTPUT_AMOUNT = 10.0;
    private static final String B_L1_AMOUNT = "12";
    private static final String B_L2_AMOUNT = "8";

    private GlobalPlanFixture globalPlanFixture;
    private GlobalPlanChainContext chain;
    private String resourceAName;
    private String resourceBName;
    private String l1StorageName;
    private String l2StorageName;
    private String resourceCName;
    private String mapM1Name;
    private String mapM2Name;
    private String mapM3Name;
    private YearMonth planPeriod;
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        chain = globalPlanFixture.prepareDecompositionChain();
        planPeriod = globalPlanFixture.nextUniquePeriod();

        ResourceResponse resourceA = chain.getResourceA();
        ResourceResponse resourceB = chain.getResourceB();
        ResourceResponse resourceC = chain.getResourceC();
        TechnologicalMapResponse mapM1 = chain.getMapM1();
        TechnologicalMapResponse mapM2 = chain.getMapM2();
        TechnologicalMapResponse mapM3 = chain.getMapM3();

        resourceAName = resourceA.getName();
        resourceBName = resourceB.getName();
        resourceCName = resourceC.getName();
        mapM1Name = mapM1.getName();
        mapM2Name = mapM2.getName();
        mapM3Name = mapM3.getName();
        l1StorageName = globalPlanFixture.resolveStorageName(chain.getL1StorageId());
        l2StorageName = globalPlanFixture.resolveStorageName(chain.getL2StorageId());

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectAllLocationsView();

        log.info("Global plan UI happy-path setup — period {}/{}, output A={}, L1={}, L2={}",
                planPeriod.getMonthValue(), planPeriod.getYear(), resourceAName, l1StorageName, l2StorageName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCreatedGlobalPlans() {
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
    @TestCaseId("TC-GP-UI-HP-001")
    @Story("Happy path — create global plan and generate location plans")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** повний happy path створення глобального плану через UI wizard (Tab 1–4).
            
            **Підготовка (API):** ланцюг техкарт M1/M2/M3, ресурси A/B/C та унікальний місяць.
            **Роль:** ADMIN (cookies через Playwright inject)
            
            **Кроки:**
            1. Tab 1 — опис, період, output A=10, «Створити план»
            2. Tab 2 — декомпозиція, ручне призначення B (12@L1 + 8@L2), «Розподілити по локаціях»
            3. Tab 3 — перегляд потреб, «Далі»
            4. Tab 4 — «Створити плани по локаціях», «Готово»
            
            **Перевірки:** вкладки 3/4 розблоковані після розподілу; badge «Створено»; повернення на список;
            на Tab 2 готовий виріб A має техкарту M1 (auto-assign на L1).
            **Артефакти:** скріншот і лог на кожному етапі.
            """)
    public void createGlobalPlanHappyPathThroughWizard() {
        final String testCaseId = "TC-GP-UI-HP-001";
        final String description = "UI-HP-" + planPeriod.getMonthValue() + "/" + planPeriod.getYear()
                + "-" + System.currentTimeMillis();

        Allure.parameter("period", planPeriod.getMonthValue() + "/" + planPeriod.getYear());
        Allure.parameter("outputResource", resourceAName);
        Allure.parameter("outputAmount", OUTPUT_AMOUNT);
        Allure.parameter("outputTechMap", mapM1Name);

        final GlobalPlanWizardPage wizard = Allure.step("Відкрити список і wizard створення", () -> {
            GlobalPlansPage listPage = new GlobalPlansPage(page).open();
            listPage.attachScreenshot(testCaseId + " — step 0 list");
            log.info("{}: opened global plans list", testCaseId);

            GlobalPlanWizardPage opened = listPage.clickCreatePlan();
            opened.attachScreenshot(testCaseId + " — step 0 wizard empty");
            assertThat(opened.isWizardHeadingVisible()).isTrue();
            assertThat(opened.areLateTabsDisabledOnFreshCreate()).isTrue();
            return opened;
        });

        Allure.step("Tab 1 — заповнити і створити план", () -> {
            wizard.fillDescription(description)
                    .selectPeriod(planPeriod.getMonthValue(), planPeriod.getYear())
                    .fillOutputProduct(resourceAName, String.valueOf((int) OUTPUT_AMOUNT));
            wizard.attachScreenshot(testCaseId + " — step 1 tab1 filled");

            wizard.submitCreatePlan();
            wizard.attachScreenshot(testCaseId + " — step 1 tab1 saved");

            Long planId = wizard.extractPlanIdFromUrl();
            globalPlanIdsToCleanup.add(planId);
            Allure.parameter("globalPlanId", planId);
            log.info("{}: global plan created via UI, id={}", testCaseId, planId);
        });

        Allure.step("Tab 2 — декомпозиція та призначення виробництва", () -> {
            wizard.waitForDecompositionIdle();
            wizard.attachScreenshot(testCaseId + " — step 2 decomposition initial");

            assertThat(wizard.hasMissingTechMapForResource(resourceAName))
                    .as("Готовий виріб «%s» має мати доступну техкарту", resourceAName)
                    .isFalse();
            wizard.verifyAssignedTechMap(resourceAName, l1StorageName, mapM1Name);
            wizard.attachScreenshot(testCaseId + " — step 2 resource A tech map verified");

            log.info("{}: assign resource B (12@L1 + 8@L2 via M2)", testCaseId);
            wizard.assignProduction(resourceBName, List.of(
                    new GlobalPlanWizardPage.ProductionAssignment(l1StorageName, mapM2Name, B_L1_AMOUNT),
                    new GlobalPlanWizardPage.ProductionAssignment(l2StorageName, mapM2Name, B_L2_AMOUNT)
            ));
            wizard.attachScreenshot(testCaseId + " — step 2 resource B assigned");

            if (!wizard.isProductionAssignedForResource(resourceCName)) {
                log.info("{}: assign resource C (10@L1 via M3)", testCaseId);
                wizard.assignProduction(resourceCName, List.of(
                        new GlobalPlanWizardPage.ProductionAssignment(l1StorageName, mapM3Name, "10")
                ));
                wizard.attachScreenshot(testCaseId + " — step 2 resource C assigned");
            }

            wizard.waitForDistributeEnabled();
            wizard.attachScreenshot(testCaseId + " — step 2 decomposition complete");

            wizard.clickDistributeToLocations();
            wizard.attachScreenshot(testCaseId + " — step 2 distributed");

            assertThat(wizard.isTabEnabled("3. Потрібно ресурсів"))
                    .as("Tab 3 має бути доступною після розподілу")
                    .isTrue();
            assertThat(wizard.isTabEnabled("4. Плани на локації"))
                    .as("Tab 4 має бути доступною після розподілу")
                    .isTrue();
        });

        Allure.step("Tab 3 — перегляд потреб ресурсів", () -> {
            assertThat(wizard.isRequirementsTabVisible())
                    .as("Tab 3 має показувати таблиці потреб")
                    .isTrue();
            wizard.attachScreenshot(testCaseId + " — step 3 requirements");

            wizard.proceedFromRequirementsTab();
            wizard.attachScreenshot(testCaseId + " — step 3 proceeded to tab 4");
        });

        Allure.step("Tab 4 — генерація планів по локаціях і завершення", () -> {
            wizard.generateLocationPlans();
            wizard.attachScreenshot(testCaseId + " — step 4 location plans generated");

            GlobalPlansPage listPage = wizard.clickDoneAndReturnToList();
            listPage.attachScreenshot(testCaseId + " — step 5 back on list");

            assertThat(listPage.isListHeadingVisible())
                    .as("Після «Готово» має відкритися список глобальних планів")
                    .isTrue();
            log.info("{}: happy path completed successfully", testCaseId);
        });
    }
}
