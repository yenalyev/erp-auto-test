package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbacFixture extends BaseFixture {

    public RbacFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Публічний API для тестів. Створює все необхідне одним викликом.
     */
    @Step("Setup complete ERP test data context")
    public void prepareFullRbacContext() {
        log.info("Starting ERP test data generation...");
        fetchSharedUnit(5);
        setupSharedResource();
        setupSharedResourceList(4);
        prepareTechMapForUpdate();
        setupDynamicProductionList(3, UserRole.OWNER_1);
        setupDynamicBusinessUnit();
        setupDynamicPlan(2);
    }
}