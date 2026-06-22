package com.erp.tests.functional.global_plan;

import com.erp.fixtures.GlobalPlanFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.util.ArrayList;
import java.util.List;

@Slf4j
abstract class GlobalPlanApiTestBase extends BaseFunctionalTest {

    protected GlobalPlanFixture globalPlanFixture;
    protected final List<Long> generatedPlanIds = new ArrayList<>();
    protected final List<Long> globalPlanIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void setupGlobalPlanApiSuite() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        globalPlanFixture.prepareDecompositionChain();
        SchemaRegistry.logSchemaCoverage();
    }

    @AfterClass(alwaysRun = true)
    public void teardownGlobalPlanApiSuite() {
        if (globalPlanFixture == null) {
            return;
        }
        globalPlanFixture.cleanupGeneratedPlans(generatedPlanIds);
        for (Long planId : globalPlanIdsToCleanup) {
            try {
                globalPlanFixture.deleteGlobalPlan(planId);
            } catch (AssertionError e) {
                log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
            }
        }
    }

    protected void trackGeneratedPlans(List<Long> planIds) {
        if (planIds != null) {
            generatedPlanIds.addAll(planIds);
        }
    }

    protected void trackGlobalPlan(Long globalPlanId) {
        if (globalPlanId != null) {
            globalPlanIdsToCleanup.add(globalPlanId);
        }
    }
}
