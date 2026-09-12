package com.erp.annotations;

import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;

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

    /** Business personas participating in the scenario; resolved through business-roles.yml. */
    BusinessRole[] roles() default {};

    /** Dynamic location templates used by the scenario; resolved through location-profiles.yml. */
    LocationProfile[] locationProfiles() default {};
}
