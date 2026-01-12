package com.erp.listeners;


import com.erp.utils.helpers.TestCaseIdExtractor;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;

/**
 * Автоматично додає Test Case ID в Allure звіти
 */
@Slf4j
public class AllureTestCaseIdListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (method.isTestMethod()) {
            Method testMethod = method.getTestMethod().getConstructorOrMethod().getMethod();

            if (TestCaseIdExtractor.hasTestCaseId(testMethod)) {
                String testCaseId = TestCaseIdExtractor.getTestCaseId(testMethod);

                // Додаємо TMS Link
                Allure.tms("TestCase", testCaseId);

                // Додаємо як label для фільтрації
                Allure.label("testCaseId", testCaseId);

                // Оновлюємо назву тесту в Allure: [TC-AUTH-001] testSuccessfulLogin
                Allure.getLifecycle().updateTestCase(tc ->
                        tc.setName("[" + testCaseId + "] " + tc.getName())
                );

                log.debug("🏷️  Test Case ID attached to Allure: {}", testCaseId);
            } else {
                log.warn("⚠️  Test method '{}' doesn't have @TestCaseId annotation",
                        testMethod.getName());
            }
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // Можна додати логіку після виконання тесту
    }
}
