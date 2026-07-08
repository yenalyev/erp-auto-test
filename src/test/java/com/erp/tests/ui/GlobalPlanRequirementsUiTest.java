package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.common.GlobalPlanChainExpectations;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.GlobalPlanWizardPage.RequirementSection;
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
 * UI verification of Tab 3 requirement amounts after full wizard decomposition.
 */
@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanRequirementsUiTest extends BaseUITest {

    private static final String B_L1_AMOUNT = "12";
    private static final String B_L2_AMOUNT = "8";

    private GlobalPlanFixture globalPlanFixture;
    private GlobalPlanChainContext chain;
    private String resourceAName;
    private String resourceBName;
    private String resourceCName;
    private String resourceXName;
    private String resourceYName;
    private String resourceZName;
    private String l1StorageName;
    private String l2StorageName;
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
        TechnologicalMapResponse mapM2 = chain.getMapM2();
        TechnologicalMapResponse mapM3 = chain.getMapM3();

        resourceAName = resourceA.getName();
        resourceBName = resourceB.getName();
        resourceCName = resourceC.getName();
        resourceXName = chain.getResourceX().getName();
        resourceYName = chain.getResourceY().getName();
        resourceZName = chain.getResourceZ().getName();
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
    @TestCaseId("TC-GP-UI-028")
    @Story("Tab 3 requirement amounts")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** Tab 3 «Потрібно ресурсів» показує точні кількості напівфабрикатів і сировини
            після повної декомпозиції ланцюга M1/M2/M3 (output A=10).
            
            **Очікування:** B=20, C=20 (напівфабрикати); x=30, y=40, z=20 (сировина).
            """)
    public void requirementsTabShowsExactIngredientAmounts() {
        final String testCaseId = "TC-GP-UI-028";
        final String description = "UI-REQ-" + planPeriod.getMonthValue() + "/" + planPeriod.getYear()
                + "-" + System.currentTimeMillis();

        GlobalPlanWizardPage wizard = new GlobalPlansPage(page).open().clickCreatePlan();

        wizard.fillDescription(description)
                .selectPeriod(planPeriod.getMonthValue(), planPeriod.getYear())
                .fillOutputProduct(resourceAName, String.valueOf((int) GlobalPlanChainExpectations.OUTPUT_A))
                .submitCreatePlan();

        Long planId = wizard.extractPlanIdFromUrl();
        globalPlanIdsToCleanup.add(planId);

        wizard.waitForDecompositionIdle();
        wizard.assignProduction(resourceBName, List.of(
                new GlobalPlanWizardPage.ProductionAssignment(l1StorageName, mapM2Name, B_L1_AMOUNT),
                new GlobalPlanWizardPage.ProductionAssignment(l2StorageName, mapM2Name, B_L2_AMOUNT)));
        if (!wizard.isProductionAssignedForResource(resourceCName)) {
            wizard.assignProduction(resourceCName, List.of(
                    new GlobalPlanWizardPage.ProductionAssignment(l1StorageName, mapM3Name, "20")));
        }
        wizard.waitForDistributeEnabled().clickDistributeToLocations();

        assertThat(wizard.isRequirementsTabVisible()).isTrue();
        wizard.attachScreenshot(testCaseId + " — tab3 requirements");

        wizard.verifyRequirementAmount(resourceBName, "20", RequirementSection.SEMI_FINISHED);
        wizard.verifyRequirementAmount(resourceCName, "20", RequirementSection.SEMI_FINISHED);
        wizard.verifyRequirementAmount(resourceXName, "30", RequirementSection.RAW_MATERIALS);
        wizard.verifyRequirementAmount(resourceYName, "40", RequirementSection.RAW_MATERIALS);
        wizard.verifyRequirementAmount(resourceZName, "20", RequirementSection.RAW_MATERIALS);

        wizard.attachScreenshot(testCaseId + " — tab3 amounts verified");
        log.info("{}: Tab 3 requirement amounts verified", testCaseId);
    }
}
