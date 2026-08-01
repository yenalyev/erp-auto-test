package com.erp.listeners;

import com.erp.dto.tcm.TcmSuiteDto;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.TestCaseIdExtractor;
import com.erp.utils.helpers.TcmApiClient;
import com.erp.utils.helpers.TcmScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class TcmScopeListener implements ISuiteListener, IMethodInterceptor {

    @Override
    public void onStart(ISuite suite) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            return;
        }

        Long featureId = ConfigProvider.getTcmFeatureId();
        Long acId = ConfigProvider.getTcmAcId();
        if (featureId == null && acId == null) {
            return;
        }

        try {
            TcmSuiteDto suiteDto = featureId != null
                    ? TcmApiClient.fetchFeatureSuite(featureId)
                    : TcmApiClient.fetchAcSuite(acId);
            Set<String> allowedIds = suiteDto.getAutomationTestIds().stream()
                    .map(id -> id.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            TcmScopeContext.set(featureId, acId, suiteDto.getTestPlanId(), allowedIds);
            log.info("TCM scope loaded: type={}, id={}, automationIds={}",
                    suiteDto.getScopeType(),
                    suiteDto.getScopeId(),
                    allowedIds.size());
            if (allowedIds.isEmpty()) {
                log.warn("TCM scope has no automation test IDs — all @TestCaseId tests will be skipped");
            }
        } catch (Exception e) {
            log.error("Failed to load TCM scope: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        TcmScopeContext.clear();
    }

    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        if (!TcmScopeContext.isActive()) {
            return methods;
        }
        Set<String> allowed = TcmScopeContext.getAllowedTestCaseIds();
        return methods.stream()
                .filter(method -> {
                    var ids = TestCaseIdExtractor.getTestCaseIds(
                            method.getMethod().getConstructorOrMethod().getMethod());
                    if (ids.isEmpty()) {
                        return false;
                    }
                    return ids.stream()
                            .map(id -> id.toUpperCase(Locale.ROOT))
                            .anyMatch(allowed::contains);
                })
                .toList();
    }
}
