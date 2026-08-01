package com.erp.listeners;

import com.erp.annotations.TestCaseId;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Підставляє {@link TestCaseId} у {@link ITestAnnotation#setTestName(String)} —
 * Allure TestNG бере назву з {@code ITestResult.getName()}.
 */
public class TestCaseIdAnnotationTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        if (testMethod == null) {
            return;
        }
        TestCaseId testCaseId = testMethod.getAnnotation(TestCaseId.class);
        if (testCaseId == null || testCaseId.value().length == 0) {
            return;
        }
        String id = Arrays.stream(testCaseId.value())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        if (id.isBlank()) {
            return;
        }
        String methodName = testMethod.getName();
        String desiredName = "[" + id + "] " + methodName;
        if (annotation.getTestName() == null || annotation.getTestName().isBlank()) {
            annotation.setTestName(desiredName);
        } else if (!annotation.getTestName().startsWith("[")) {
            annotation.setTestName("[" + id + "] " + annotation.getTestName());
        }
    }
}
