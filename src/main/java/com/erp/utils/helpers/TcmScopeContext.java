package com.erp.utils.helpers;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.Set;

@UtilityClass
public class TcmScopeContext {

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    public static void set(Long featureId, Long acId, Long testPlanId, Set<String> allowedTestCaseIds) {
        STATE.set(new State(featureId, acId, testPlanId,
                allowedTestCaseIds != null ? Set.copyOf(allowedTestCaseIds) : Set.of()));
    }

    public static boolean isActive() {
        State state = STATE.get();
        return state != null && !state.allowedTestCaseIds.isEmpty();
    }

    public static Long getFeatureId() {
        State state = STATE.get();
        return state != null ? state.featureId : null;
    }

    public static Long getAcId() {
        State state = STATE.get();
        return state != null ? state.acId : null;
    }

    public static Long getTestPlanId() {
        State state = STATE.get();
        return state != null ? state.testPlanId : null;
    }

    public static Set<String> getAllowedTestCaseIds() {
        State state = STATE.get();
        return state != null ? state.allowedTestCaseIds : Collections.emptySet();
    }

    public static void clear() {
        STATE.remove();
    }

    private record State(
            Long featureId,
            Long acId,
            Long testPlanId,
            Set<String> allowedTestCaseIds
    ) {
    }
}
