package com.erp.utils.helpers;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.Set;

/**
 * Suite-wide TCM scope. Must not be ThreadLocal: TestNG {@code parallel="classes"}
 * runs {@link org.testng.IMethodInterceptor} and tests on worker threads.
 */
@UtilityClass
public class TcmScopeContext {

    private static volatile State state;

    public static void set(Long featureId, Long acId, Long testPlanId, Set<String> allowedTestCaseIds) {
        state = new State(featureId, acId, testPlanId,
                allowedTestCaseIds != null ? Set.copyOf(allowedTestCaseIds) : Set.of());
    }

    public static boolean isLoaded() {
        return state != null;
    }

    /** Scope was loaded from TCM (including empty ID set — run nothing). */
    public static boolean isActive() {
        return state != null;
    }

    public static Long getFeatureId() {
        State current = state;
        return current != null ? current.featureId : null;
    }

    public static Long getAcId() {
        State current = state;
        return current != null ? current.acId : null;
    }

    public static Long getTestPlanId() {
        State current = state;
        return current != null ? current.testPlanId : null;
    }

    public static Set<String> getAllowedTestCaseIds() {
        State current = state;
        return current != null ? current.allowedTestCaseIds : Collections.emptySet();
    }

    public static void clear() {
        state = null;
    }

    private record State(
            Long featureId,
            Long acId,
            Long testPlanId,
            Set<String> allowedTestCaseIds
    ) {
    }
}
