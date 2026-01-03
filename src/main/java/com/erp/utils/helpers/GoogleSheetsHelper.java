package com.erp.utils.helpers;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public class GoogleSheetsHelper {

    private static final String APPLICATION_NAME = "ERP Test Framework";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FILE_PATH = "src/test/resources/google-credentials.json";

    private final Sheets sheetsService;
    private final String spreadsheetId;

    // Sheet names
    private static final String TEST_RESULTS_SHEET = "Test Results";
    private static final String TRACEABILITY_SHEET = "Traceability Matrix";

    public GoogleSheetsHelper(String spreadsheetId) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        this.sheetsService = getSheetsService();
        log.info("📊 GoogleSheetsHelper initialized for spreadsheet: {}", spreadsheetId);
    }

    /**
     * Створення Sheets service з новим Google Auth API
     */
    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // ✅ Новий API замість deprecated GoogleCredential
        GoogleCredentials credentials;
        try (InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH)) {
            credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
        }

        return new Sheets.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Додати результат тесту
     */
    public void appendTestResult(TestResult result) throws IOException {
        List<Object> row = Arrays.asList(
                result.getTestId(),
                result.getTestName(),
                result.getStatus(),
                result.getExecutionTime(),
                result.getDate(),
                result.getEnvironment(),
                result.getUser(),
                result.getErrorMessage() != null ? result.getErrorMessage() : ""
        );

        appendRow(TEST_RESULTS_SHEET + "!A:H", row);
        log.info("✅ Test result saved: {} - {}", result.getTestId(), result.getStatus());
    }

    /**
     * Оновити Traceability Matrix
     */
    public void updateTraceability(String requirementId, String testId, String testName, String status) throws IOException {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        List<Object> row = Arrays.asList(requirementId, testId, testName, status, date);

        // Шукаємо чи вже є такий requirement
        String existingRow = findRequirementRow(requirementId);

        if (existingRow != null) {
            // Оновлюємо існуючий рядок
            updateRow(TRACEABILITY_SHEET + "!" + existingRow, row);
            log.debug("📝 Updated traceability: {}", requirementId);
        } else {
            // Додаємо новий рядок
            appendRow(TRACEABILITY_SHEET + "!A:E", row);
            log.debug("➕ Added new traceability: {}", requirementId);
        }
    }

    /**
     * Знайти рядок з requirement ID
     */
    private String findRequirementRow(String requirementId) throws IOException {
        List<List<Object>> values = readSheet(TRACEABILITY_SHEET + "!A:A");

        if (values == null || values.isEmpty()) {
            return null;
        }

        for (int i = 0; i < values.size(); i++) {
            if (!values.get(i).isEmpty() && values.get(i).get(0).equals(requirementId)) {
                return "A" + (i + 1) + ":E" + (i + 1);
            }
        }

        return null;
    }

    /**
     * Додати рядок в кінець таблиці
     */
    public void appendRow(String range, List<Object> values) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(values));

        sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Оновити конкретний рядок
     */
    public void updateRow(String range, List<Object> values) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(values));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Прочитати дані з таблиці
     */
    public List<List<Object>> readSheet(String range) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        return response.getValues();
    }

    /**
     * Ініціалізувати таблицю (створити headers)
     */
    public void initializeSheets() throws IOException {
        log.info("📋 Initializing Google Sheets...");

        // Headers для Test Results
        List<Object> testResultsHeaders = Arrays.asList(
                "Test ID", "Test Name", "Status", "Execution Time",
                "Date", "Environment", "User", "Error Message"
        );

        // Headers для Traceability Matrix
        List<Object> traceabilityHeaders = Arrays.asList(
                "Test ID", "Requirement ID", "Test Name", "Last Status", "Last Run"
        );

        try {
            // Перевіряємо чи headers вже існують
            List<List<Object>> existing = readSheet(TEST_RESULTS_SHEET + "!A1:H1");
            if (existing == null || existing.isEmpty()) {
                updateRow(TEST_RESULTS_SHEET + "!A1:H1", testResultsHeaders);
                log.info("✅ Test Results headers created");
            }
        } catch (Exception e) {
            updateRow(TEST_RESULTS_SHEET + "!A1:H1", testResultsHeaders);
            log.info("✅ Test Results headers created");
        }

        try {
            List<List<Object>> existing = readSheet(TRACEABILITY_SHEET + "!A1:E1");
            if (existing == null || existing.isEmpty()) {
                updateRow(TRACEABILITY_SHEET + "!A1:E1", traceabilityHeaders);
                log.info("✅ Traceability Matrix headers created");
            }
        } catch (Exception e) {
            updateRow(TRACEABILITY_SHEET + "!A1:E1", traceabilityHeaders);
            log.info("✅ Traceability Matrix headers created");
        }
    }

    /**
     * Model для результату тесту
     */
    @lombok.Data
    @lombok.Builder
    public static class TestResult {
        private String testId;
        private String testName;
        private String status;
        private String executionTime;
        private String date;
        private String environment;
        private String user;
        private String errorMessage;
    }
}