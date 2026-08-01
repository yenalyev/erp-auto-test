package com.erp.utils.helpers;

import com.erp.annotations.TestCaseId;
import lombok.experimental.UtilityClass;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@UtilityClass
public class TestCaseIdExtractor {

    /**
     * Primary Test Case ID (first value) — used for screenshots / single-id displays.
     */
    public static String getTestCaseId(ITestResult result) {
        return getTestCaseId(result.getMethod().getConstructorOrMethod().getMethod());
    }

    /**
     * Primary Test Case ID (first value).
     */
    public static String getTestCaseId(Method method) {
        List<String> ids = getTestCaseIds(method);
        return ids.isEmpty() ? "NO_ID" : ids.getFirst();
    }

    /**
     * All ids from {@link TestCaseId} (primary + TCM aliases), or empty when absent.
     */
    public static List<String> getTestCaseIds(Method method) {
        TestCaseId annotation = method.getAnnotation(TestCaseId.class);
        if (annotation == null || annotation.value().length == 0) {
            return List.of();
        }
        return Arrays.stream(annotation.value())
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }

    public static List<String> getTestCaseIds(ITestResult result) {
        return getTestCaseIds(result.getMethod().getConstructorOrMethod().getMethod());
    }

    public static boolean hasTestCaseId(Method method) {
        return !getTestCaseIds(method).isEmpty();
    }
}
