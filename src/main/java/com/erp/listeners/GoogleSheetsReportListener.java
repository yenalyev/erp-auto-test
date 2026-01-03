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
    private long startTime;

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

    @Override
    public void onTestStart(ITestResult result) {
        startTime = System.currentTimeMillis();
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
            long executionTime = System.currentTimeMillis() - startTime;
            String formattedTime = String.format("%.2fs", executionTime / 1000.0);
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Використовуємо новий екстрактор
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

            sheetsHelper.appendTestResult(testResult);

            // Оновити traceability matrix
            if (requirementId != null && !requirementId.isEmpty()) {
                sheetsHelper.updateTraceability(testId, requirementId, testName, status);
            }

            log.debug("📝 Test result saved: {} - {}", testId, status);

        } catch (Exception e) {
            log.error("❌ Failed to save test result to Google Sheets: {}", e.getMessage());
        }
    }

    /**
     * Витягує Requirement ID з @Story анотації
     * @Story("REQ-AUTH-001: User Authentication") → "REQ-AUTH-001"
     */
    private String extractRequirementId(ITestResult result) {
        try {
            Story story = result.getMethod()
                    .getConstructorOrMethod()
                    .getMethod()
                    .getAnnotation(Story.class);

            if (story != null && story.value() != null) {
                String value = story.value();
                // Витягуємо ID з формату "REQ-AUTH-001: Description"
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