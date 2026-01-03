package com.erp.data.strategies;

import com.erp.data.RbacTestContext;

/**
 * Strategy для генерації request body різних типів
 */
@FunctionalInterface
public interface RequestBodyStrategy {
    Object generate(RbacTestContext context);
}