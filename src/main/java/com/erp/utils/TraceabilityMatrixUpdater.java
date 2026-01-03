package com.erp.utils;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.GoogleSheetsHelper;
import com.erp.utils.parser.AllureResultsParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Slf4j
public class TraceabilityMatrixUpdater {

    public static void updateTraceabilityMatrix() {
        if (!ConfigProvider.isGoogleSheetsEnabled()) {
            log.info("📊 Google Sheets reporting is disabled");
            return;
        }

        try {
            String spreadsheetId = ConfigProvider.getGoogleSheetsSpreadsheetId();
            log.info("📊 Updating traceability matrix in spreadsheet: {}", spreadsheetId);

            // Парсимо результати Allure
            AllureResultsParser parser = new AllureResultsParser();
            List<AllureResultsParser.TestResult> results = parser.parseAllureResults();

            if (results.isEmpty()) {
                log.warn("⚠️  No Allure results found to process");
                return;
            }

            // Підключаємось до Google Sheets
            GoogleSheetsHelper sheetsHelper = new GoogleSheetsHelper(spreadsheetId);
            sheetsHelper.initializeSheets();

            // Додаємо всі результати
            int successCount = 0;
            for (AllureResultsParser.TestResult result : results) {
                try {
                    // Конвертуємо AllureResultsParser.TestResult → GoogleSheetsHelper.TestResult
                    GoogleSheetsHelper.TestResult sheetResult = GoogleSheetsHelper.TestResult.builder()
                            .testId(result.getStory())
                            .testName(result.getTestMethod())
                            .status(result.getStatus())
                            .executionTime(result.getDuration())
                            .date(result.getLastRun())
                            .environment(System.getProperty("env", "unknown"))
                            .user(ConfigProvider.getAuthUsername())
                            .errorMessage(result.getBugId())
                            .build();

                    sheetsHelper.appendTestResult(sheetResult);

                    // Оновлюємо traceability matrix
                    if (result.getStory() != null && !result.getStory().isEmpty()) {
                        sheetsHelper.updateTraceability(
                                result.getStory(),
                                result.getStory(),
                                result.getTestMethod(),
                                result.getStatus()
                        );
                    }

                    successCount++;
                } catch (Exception e) {
                    log.error("❌ Failed to process result for {}: {}",
                            result.getTestMethod(), e.getMessage());
                }
            }

            log.info("✅ Traceability matrix updated successfully! {} of {} results added.",
                    successCount, results.size());

        } catch (GeneralSecurityException | IOException e) {
            log.error("❌ Error updating traceability matrix: {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        log.info("🚀 Starting Traceability Matrix Updater...");
        updateTraceabilityMatrix();
    }
}