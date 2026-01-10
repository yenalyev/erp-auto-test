package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceFixture extends BaseFixture {
    public ResourceFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Create test data for Measurement Unit functional tests
     */
    @Step("Setup Measurement Unit functional test data context")
    public void prepareContext() {
        log.info("Starting Measurement Unit functional test data generation...");
        fetchSharedUnit(5);
        setupSharedResourceList(5);
    }

}
