package com.erp.data.strategies;

import com.erp.test_context.RbacTestContext;
import com.erp.test_context.TestContext;

/**
 * Strategy для генерації request body різних типів
 */
@FunctionalInterface
public interface RequestBodyStrategy {
    Object generate(TestContext context);
}