package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.test_context.TestContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class BaseFixture {
    protected final TestContext testContext;
    protected final ApiExecutor apiExecutor;
}