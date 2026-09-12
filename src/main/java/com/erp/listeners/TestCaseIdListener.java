package com.erp.listeners;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.utils.helpers.TestCaseIdExtractor;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.List;

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
        List<String> ids = TestCaseIdExtractor.getTestCaseIds(testMethod);
        if (ids.isEmpty()) {
            log.warn("⚠️  Test method '{}' doesn't have @TestCaseId annotation", testMethod.getName());
            return;
        }

        for (String testCaseId : ids) {
            Allure.tms("TestCase", testCaseId);
            Allure.label("testCaseId", testCaseId);
        }
        TestCaseId metadata = testMethod.getAnnotation(TestCaseId.class);
        for (BusinessRole role : metadata.roles()) {
            Allure.label("businessRole", role.name());
        }
        for (LocationProfile profile : metadata.locationProfiles()) {
            Allure.label("locationProfile", profile.name());
        }
        log.info("🏷️  Test Case ID: {}", String.join(", ", ids));
        if (metadata.roles().length > 0) {
            log.info("👤 Business roles: {}", List.of(metadata.roles()));
        }
        if (metadata.locationProfiles().length > 0) {
            log.info("📍 Location profiles: {}", List.of(metadata.locationProfiles()));
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        Method testMethod = method.getTestMethod().getConstructorOrMethod().getMethod();
        List<String> ids = TestCaseIdExtractor.getTestCaseIds(testMethod);
        if (ids.isEmpty()) {
            return;
        }
        String prefix = "[" + String.join(", ", ids) + "] ";
        Allure.getLifecycle().updateTestCase(tc -> {
            String currentName = tc.getName();
            if (currentName != null && !currentName.startsWith("[")) {
                tc.setName(prefix + currentName);
            }
        });
    }
}
