package com.erp.listeners;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.dto.tcm.TcmRunImportRequest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.TcmScopeContext;
import com.erp.utils.helpers.TestCaseIdExtractor;
import com.erp.utils.helpers.TcmApiClient;
import com.erp.utils.helpers.TcmResultOutbox;
import lombok.extern.slf4j.Slf4j;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TcmReportListener implements ITestListener, ISuiteListener {

    private final ThreadLocal<Long> startTime = new ThreadLocal<>();
    private final Map<ISuite, SuiteRun> runs = new ConcurrentHashMap<>();

    private record SuiteRun(String name, String remoteRunId, TcmResultOutbox outbox,
                            List<TcmApiClient.BufferedResult> results) {
    }

    @Override
    public void onStart(ISuite suite) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            log.info("TCM reporting is disabled");
            return;
        }
        String remoteRunId = TcmApiClient.resolveSuiteRemoteRunId(null);
        TcmResultOutbox outbox = new TcmResultOutbox(remoteRunId);
        outbox.beginRun();
        runs.put(suite, new SuiteRun(suite.getName(), remoteRunId, outbox,
                Collections.synchronizedList(new ArrayList<>())));
        log.info("TCM listener initialized for suite: {} remoteRunId={} (outbox={})",
                suite.getName(), remoteRunId, outbox.resultsFile());
    }

    @Override
    public void onFinish(ISuite suite) {
        if (!ConfigProvider.isTcmReportingEnabled()) {
            return;
        }
        SuiteRun run = runs.remove(suite);
        if (run == null) {
            throw new IllegalStateException("TCM suite was not initialized: " + suite.getName());
        }
        List<TcmApiClient.BufferedResult> toSend;
        synchronized (run.results()) {
            toSend = List.copyOf(run.results());
        }
        if (toSend.isEmpty()) {
            toSend = run.outbox().readAll();
        }
        if (toSend.isEmpty()) {
            boolean scoped = TcmScopeContext.isActive()
                    || ConfigProvider.getTcmFeatureId() != null
                    || ConfigProvider.getTcmAcId() != null;
            if (scoped) {
                String ids = String.join(", ", TcmScopeContext.getAllowedTestCaseIds());
                throw new IllegalStateException(
                        "Жоден тест не виконано для TCM scope. "
                                + "Очікувані automation ID: " + (ids.isBlank() ? "(порожньо)" : ids));
            }
            log.info("No test results to send to TCM");
            return;
        }

        try {
            TcmRunImportRequest request = TcmApiClient.buildRequest(
                    run.name(),
                    toSend
            );
            request.setImportSource("LISTENER");
            request.setRemoteRunId(run.remoteRunId());
            TcmImportResponse response = TcmApiClient.submitRunWithRetry(request, 3);
            run.outbox().writeImportOk(response, "LISTENER");
            log.info("TCM import complete: runId={}, matched={}, skippedManual={}, unmatched={}",
                    response.getRunId(),
                    response.getMatched(),
                    response.getSkippedManual(),
                    response.getUnmatched());
        } catch (Exception e) {
            log.error("Failed to send results to TCM: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to send results to TCM: " + e.getMessage(), e);
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
        bufferResult(result, ITestResult.SKIP, skipReason(result));
    }

    /**
     * Prefer the real SkipException / @BeforeMethod failure message so TCM
     * actualResult is diagnosable (was hardcoded "Test was skipped").
     */
    private static String skipReason(ITestResult result) {
        Throwable t = result.getThrowable();
        if (t == null) {
            return "Test was skipped (no throwable)";
        }
        String message = t.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return t.getClass().getSimpleName();
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

        LocalDateTime executedAt = LocalDateTime.now();
        SuiteRun run = runs.get(result.getTestContext().getSuite());
        if (run == null) {
            throw new IllegalStateException("TCM suite was not initialized for " + testCaseId);
        }
        for (String id : TestCaseIdExtractor.getTestCaseIds(result)) {
            run.results().add(new TcmApiClient.BufferedResult(
                    id,
                    status,
                    durationMs,
                    errorMessage,
                    executedAt
            ));
            run.outbox().append(id, mapStatus(status), durationMs, errorMessage, executedAt);
        }
    }

    private static String mapStatus(int testngStatus) {
        return switch (testngStatus) {
            case ITestResult.SUCCESS -> "PASS";
            case ITestResult.FAILURE -> "FAIL";
            case ITestResult.SKIP -> "SKIPPED";
            default -> "NOT_RUN";
        };
    }
}
