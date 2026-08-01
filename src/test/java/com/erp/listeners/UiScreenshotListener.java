package com.erp.listeners;

import com.erp.tests.ui.BaseUITest;
import com.erp.utils.helpers.AllureScreenshots;
import com.erp.utils.helpers.TestCaseIdExtractor;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Captures a UI screenshot after the {@code @Test} body finishes but
 * <em>before</em> {@code AllureTestNg#onTestSuccess/Failure} writes the result
 * (and before {@code @AfterMethod}, which is too late for Allure storage).
 */
public class UiScreenshotListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (method.isTestMethod()) {
            AllureScreenshots.rememberCurrentTest();
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        AllureScreenshots.rememberCurrentTest();
        Object instance = testResult.getInstance();
        if (instance instanceof BaseUITest uiTest) {
            String tcId = TestCaseIdExtractor.getTestCaseId(testResult);
            String step = testResult.isSuccess() ? "final state" : "failure";
            String label = "NO_ID".equals(tcId)
                    ? "Screenshot - " + testResult.getName() + " - " + step
                    : tcId + " - " + step;
            uiTest.attachScreenshot(label);
        }
    }
}
