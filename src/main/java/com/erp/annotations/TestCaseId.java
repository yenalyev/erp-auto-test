package com.erp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Business test-case id(s) for Allure / TCM reporting.
 * <p>Single id: {@code @TestCaseId("TC-FOO-001")}
 * <p>Aliases (sibling TCM ids covering the same scenario):
 * {@code @TestCaseId({"TC-PRD-001", "TC-MFG-005"})}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestCaseId {
    String[] value();
}
