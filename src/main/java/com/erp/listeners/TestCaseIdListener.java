package com.erp.listeners;

import com.erp.annotations.TestCaseId;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;

@Slf4j
public class TestCaseIdListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (method.isTestMethod()) {
            Method testMethod = method.getTestMethod().getConstructorOrMethod().getMethod();
            TestCaseId annotation = testMethod.getAnnotation(TestCaseId.class);

            if (annotation != null) {
                String testCaseId = annotation.value();

                // Allure TMS link (TCM redirect by testId)
                Allure.tms("TestCase", testCaseId);

                // Додаємо як label для фільтрації
                Allure.label("testCaseId", testCaseId);

                // Додаємо ID до назви тесту в Allure
                Allure.getLifecycle().updateTestCase(tc ->
                        tc.setName("[" + testCaseId + "] " + tc.getName())
                );

                // Логуємо
                log.info("🏷️  Test Case ID: {}", testCaseId);
            } else {
                log.warn("⚠️  Test method '{}' doesn't have @TestCaseId annotation",
                        testMethod.getName());
            }
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // Optional: можна додати логіку після виконання
    }
}