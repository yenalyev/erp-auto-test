package com.erp.listeners;

import com.erp.utils.helpers.TestCaseIdExtractor;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;

/**
 * Додає {@link com.erp.annotations.TestCaseId} у Allure: label і TMS-link.
 * Назву тесту задає {@link TestCaseIdAnnotationTransformer}.
 */
@Slf4j
public class TestCaseIdListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        Method testMethod = method.getTestMethod().getConstructorOrMethod().getMethod();
        if (!TestCaseIdExtractor.hasTestCaseId(testMethod)) {
            log.warn("⚠️  Test method '{}' doesn't have @TestCaseId annotation", testMethod.getName());
            return;
        }

        String testCaseId = TestCaseIdExtractor.getTestCaseId(testMethod);
        if (testCaseId == null || testCaseId.isBlank() || "NO_ID".equals(testCaseId)) {
            return;
        }

        Allure.tms("TestCase", testCaseId);
        Allure.label("testCaseId", testCaseId);
        log.info("🏷️  Test Case ID: {}", testCaseId);
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        Method testMethod = method.getTestMethod().getConstructorOrMethod().getMethod();
        if (!TestCaseIdExtractor.hasTestCaseId(testMethod)) {
            return;
        }
        String testCaseId = TestCaseIdExtractor.getTestCaseId(testMethod);
        if (testCaseId == null || testCaseId.isBlank() || "NO_ID".equals(testCaseId)) {
            return;
        }
        String prefix = "[" + testCaseId + "] ";
        Allure.getLifecycle().updateTestCase(tc -> {
            String currentName = tc.getName();
            if (currentName != null && !currentName.startsWith(prefix)) {
                tc.setName(prefix + currentName);
            }
        });
    }
}
