package com.erp.utils.helpers;

import com.erp.annotations.TestCaseId;
import lombok.experimental.UtilityClass;
import org.testng.ITestResult;

import java.lang.reflect.Method;

@UtilityClass
public class TestCaseIdExtractor {

    /**
     * Витягує Test Case ID з ITestResult (використовується в TestNG listeners)
     */
    public static String getTestCaseId(ITestResult result) {
        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        TestCaseId annotation = method.getAnnotation(TestCaseId.class);
        return annotation != null ? annotation.value() : "NO_ID";
    }

    /**
     * Витягує Test Case ID з Method (використовується в рефлексії)
     */
    public static String getTestCaseId(Method method) {
        TestCaseId annotation = method.getAnnotation(TestCaseId.class);
        return annotation != null ? annotation.value() : "NO_ID";
    }

    /**
     * Перевіряє, чи має метод анотацію @TestCaseId
     */
    public static boolean hasTestCaseId(Method method) {
        return method.getAnnotation(TestCaseId.class) != null;
    }
}
