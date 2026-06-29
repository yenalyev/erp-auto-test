package com.erp.listeners;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.dto.tcm.TcmRunImportRequest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.TestCaseIdExtractor;
import com.erp.utils.helpers.TcmApiClient;
import lombok.extern.slf4j.Slf4j;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class TcmReportListener implements ITestListener, ISuiteListener {

    private final ThreadLocal<Long> startTime = new ThreadLocal<>();
    private final List<TcmApiClient.BufferedResult> bufferedResults =
            Collections.synchronizedList(new ArrayList<>());
    private String suiteName;

    @Override
    public void onStart(ISuite suite) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            log.info("TCM reporting is disabled");
            return;
        }
        suiteName = suite.getName();
        log.info("TCM listener initialized for suite: {}", suiteName);
    }

    @Override
    public void onFinish(ISuite suite) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            return;
        }
        if (bufferedResults.isEmpty()) {
            log.info("No test results to send to TCM");
            return;
        }

        try {
            TcmRunImportRequest request = TcmApiClient.buildRequest(
                    suiteName != null ? suiteName : suite.getName(),
                    List.copyOf(bufferedResults)
            );
            TcmImportResponse response = TcmApiClient.submitRun(request);
            log.info("TCM import complete: runId={}, matched={}, skippedManual={}, unmatched={}",
                    response.getRunId(),
                    response.getMatched(),
                    response.getSkippedManual(),
                    response.getUnmatched());
        } catch (Exception e) {
            log.error("Failed to send results to TCM: {}", e.getMessage(), e);
        } finally {
            bufferedResults.clear();
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        if (ConfigProvider.isTcmReportingEnabled()) {
            startTime.set(System.currentTimeMillis());
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        bufferResult(result, ITestResult.SUCCESS, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String error = result.getThrowable() != null ? result.getThrowable().getMessage() : "Unknown error";
        bufferResult(result, ITestResult.FAILURE, error);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        bufferResult(result, ITestResult.SKIP, "Test was skipped");
    }

    private void bufferResult(ITestResult result, int status, String errorMessage) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            return;
        }

        long start = startTime.get() != null ? startTime.get() : System.currentTimeMillis();
        long durationMs = System.currentTimeMillis() - start;
        startTime.remove();

        String testCaseId = TestCaseIdExtractor.getTestCaseId(result);
        if ("NO_ID".equals(testCaseId)) {
            log.warn("Skipping TCM report for test without @TestCaseId: {}",
                    result.getMethod().getMethodName());
            return;
        }

        bufferedResults.add(new TcmApiClient.BufferedResult(
                testCaseId,
                status,
                durationMs,
                errorMessage,
                LocalDateTime.now()
        ));
    }
}
