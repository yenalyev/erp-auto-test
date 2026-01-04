package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.test_context.TestContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor // Це автоматично створить конструктор з 2 аргументами
public abstract class BaseFixture {
    protected final TestContext testContext;
    protected final ApiExecutor apiExecutor;
}