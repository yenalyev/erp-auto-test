package com.erp.utils;

import com.erp.utils.helpers.GoogleSheetsHelper;
import com.erp.utils.parser.AllureResultsParser;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class TraceabilityMatrixUpdater {
    private static final String SPREADSHEET_ID = "10q5vjXWjsaq6E8DeeRbpPU2HbMfcoDDSeeyg5UkDhTk";

    public static void updateTraceabilityMatrix() {
        try {
            // Парсимо результати Allure
            AllureResultsParser parser = new AllureResultsParser();
            List<AllureResultsParser.TestResult> results = parser.parseAllureResults();

            // Підключаємось до Google Sheets
            GoogleSheetsHelper sheetsHelper = new GoogleSheetsHelper(SPREADSHEET_ID);

            // Очищуємо попередні дані (крім заголовка)
            System.out.println("Updating traceability matrix...");

            // Додаємо заголовок якщо таблиця порожня
            List<List<Object>> existingData = sheetsHelper.readSheet("Sheet1!A1:I1");
            if (existingData == null || existingData.isEmpty()) {
                List<Object> header = Arrays.asList(
                        "Test Class", "Test Method", "Epic", "Feature",
                        "Story", "Status", "Last Run", "Duration", "Bug ID"
                );
                sheetsHelper.appendRow(header);
            }

            // Додаємо всі результати
            for (AllureResultsParser.TestResult result : results) {
                sheetsHelper.appendRow(result.toRowData());
            }

            System.out.println("✅ Traceability matrix updated successfully! " +
                    results.size() + " test results added.");

        } catch (GeneralSecurityException | IOException e) {
            System.err.println("❌ Error updating traceability matrix: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        updateTraceabilityMatrix();
    }
}
