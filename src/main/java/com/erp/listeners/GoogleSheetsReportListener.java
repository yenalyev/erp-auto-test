package com.erp.listeners;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.GoogleSheetsHelper;
import com.erp.utils.helpers.TestCaseIdExtractor;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GoogleSheetsReportListener implements ITestListener, ISuiteListener {

    private GoogleSheetsHelper sheetsHelper;
    // ✅ Використовуємо ThreadLocal для коректного розрахунку часу в паралельних тестах
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public void onStart(ISuite suite) {
        if (!ConfigProvider.isGoogleSheetsEnabled()) {
            log.info("📊 Google Sheets reporting is disabled");
            return;
        }

        try {
            String spreadsheetId = ConfigProvider.getGoogleSheetsSpreadsheetId();
            sheetsHelper = new GoogleSheetsHelper(spreadsheetId);
            sheetsHelper.initializeSheets();
            log.info("✅ Google Sheets listener initialized");
        } catch (Exception e) {
            log.error("❌ Failed to initialize Google Sheets: {}", e.getMessage());
        }
    }

    /**
     * 🔥 КРИТИЧНО: Викликаємо flushAll() після завершення всіх тестів сюїти
     */
    @Override
    public void onFinish(ISuite suite) {
        if (sheetsHelper != null) {
            try {
                sheetsHelper.flushAll();
                log.info("✅ All buffered results have been flushed to Google Sheets");
            } catch (Exception e) {
                log.error("❌ Failed to flush results to Google Sheets: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        startTime.set(System.currentTimeMillis());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        saveTestResult(result, "PASSED", null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String errorMessage = result.getThrowable() != null ?
                result.getThrowable().getMessage() : "Unknown error";
        saveTestResult(result, "FAILED", errorMessage);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        saveTestResult(result, "SKIPPED", "Test was skipped");
    }

    private void saveTestResult(ITestResult result, String status, String errorMessage) {
        if (sheetsHelper == null) {
            return;
        }

        try {
            // ✅ Розрахунок часу з ThreadLocal
            long start = startTime.get() != null ? startTime.get() : System.currentTimeMillis();
            long duration = System.currentTimeMillis() - start;
            startTime.remove(); // Очищуємо пам'ять потоку

            String formattedTime = String.format("%.2fs", duration / 1000.0);
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String testId = TestCaseIdExtractor.getTestCaseId(result);
            String requirementId = extractRequirementId(result);
            String testName = result.getMethod().getMethodName();
            String environment = System.getProperty("env", "debug");
            String user = ConfigProvider.getAuthUsername();

            GoogleSheetsHelper.TestResult testResult = GoogleSheetsHelper.TestResult.builder()
                    .testId(testId)
                    .testName(testName)
                    .status(status)
                    .executionTime(formattedTime)
                    .date(date)
                    .environment(environment)
                    .user(user)
                    .errorMessage(errorMessage)
                    .build();

            // Тепер ці методи просто додають дані в буфер (без HTTP запиту)
            sheetsHelper.appendTestResult(testResult);

            if (requirementId != null && !requirementId.isEmpty()) {
                // ⚠️ Виправив порядок аргументів: спочатку requirementId, потім testId
                sheetsHelper.updateTraceability(requirementId, testId, testName, status);
            }

        } catch (Exception e) {
            log.error("❌ Error queueing result for Google Sheets: {}", e.getMessage());
        }
    }

    private String extractRequirementId(ITestResult result) {
        try {
            Story story = result.getMethod()
                    .getConstructorOrMethod()
                    .getMethod()
                    .getAnnotation(Story.class);

            if (story != null && story.value() != null) {
                String value = story.value();
                if (value.contains(":")) {
                    return value.split(":")[0].trim();
                }
                return value;
            }
        } catch (Exception e) {
            log.debug("No @Story annotation found for requirement tracking");
        }
        return null;
    }
}