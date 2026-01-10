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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GoogleSheetsHelper {

    private static final String APPLICATION_NAME = "ERP Test Framework";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FILE_PATH = "src/test/resources/google-credentials.json";

    private final Sheets sheetsService;
    private final String spreadsheetId;

    // Назви аркушів
    private static final String TEST_RESULTS_SHEET = "Test Results";
    private static final String TRACEABILITY_SHEET = "Traceability Matrix";

    // Потокобезпечні буфери для збору даних перед відправкою
    private final List<TestResult> resultBuffer = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Object>> traceabilityBuffer = new ConcurrentHashMap<>();

    public GoogleSheetsHelper(String spreadsheetId) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        this.sheetsService = getSheetsService();
        log.info("📊 GoogleSheetsHelper initialized for spreadsheet: {}", spreadsheetId);
    }

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
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
     * Додає результат тесту в чергу (буфер) замість миттєвого запису
     */
    public void appendTestResult(TestResult result) {
        resultBuffer.add(result);
        log.debug("📥 Result queued for batch: {}", result.getTestId());
    }

    /**
     * Додає дані в буфер Traceability Matrix (оновлює існуючі записи в пам'яті)
     */
    public void updateTraceability(String requirementId, String testId, String testName, String status) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        List<Object> row = Arrays.asList(requirementId, testId, testName, status, date);
        traceabilityBuffer.put(requirementId, row);
    }

    /**
     * 🔥 Відправляє всі накопичені дані в Google Sheets одним батчем
     * Викликайте цей метод в @AfterSuite або через TestNG Listener (onExecutionFinish)
     */
    public void flushAll() throws IOException {
        log.info("🚀 Flushing buffers to Google Sheets...");
        flushTestResults();
        flushTraceabilityMatrix();
    }

    private void flushTestResults() throws IOException {
        if (resultBuffer.isEmpty()) return;

        List<List<Object>> values = new ArrayList<>();
        synchronized (resultBuffer) {
            for (TestResult result : resultBuffer) {
                values.add(Arrays.asList(
                        result.getTestId(),
                        result.getTestName(),
                        result.getStatus(),
                        result.getExecutionTime(),
                        result.getDate(),
                        result.getEnvironment(),
                        result.getUser(),
                        result.getErrorMessage() != null ? result.getErrorMessage() : ""
                ));
            }
            resultBuffer.clear();
        }

        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, TEST_RESULTS_SHEET + "!A:H", body)
                .setValueInputOption("RAW")
                .execute();

        log.info("✅ Successfully flushed {} test results", values.size());
    }

    private void flushTraceabilityMatrix() throws IOException {
        if (traceabilityBuffer.isEmpty()) return;

        // 1. Читаємо всю існуючу матрицю для синхронізації
        List<List<Object>> currentData = readSheet(TRACEABILITY_SHEET + "!A:E");
        if (currentData == null) currentData = new ArrayList<>();

        // 2. Оновлюємо дані в пам'яті
        for (Map.Entry<String, List<Object>> entry : traceabilityBuffer.entrySet()) {
            String reqId = entry.getKey();
            List<Object> newRow = entry.getValue();
            boolean found = false;

            for (int i = 0; i < currentData.size(); i++) {
                if (!currentData.get(i).isEmpty() && currentData.get(i).get(0).equals(reqId)) {
                    currentData.set(i, newRow);
                    found = true;
                    break;
                }
            }
            if (!found) currentData.add(newRow);
        }

        // 3. Переписуємо весь аркуш одним запитом
        ValueRange body = new ValueRange().setValues(currentData);
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, TRACEABILITY_SHEET + "!A1", body)
                .setValueInputOption("RAW")
                .execute();

        traceabilityBuffer.clear();
        log.info("✅ Traceability matrix synchronized");
    }

    public List<List<Object>> readSheet(String range) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        return response.getValues();
    }

    public void initializeSheets() throws IOException {
        log.info("📋 Initializing Google Sheets headers...");
        List<Object> testResultsHeaders = Arrays.asList(
                "Test ID", "Test Name", "Status", "Execution Time", "Date", "Environment", "User", "Error Message"
        );
        List<Object> traceabilityHeaders = Arrays.asList(
                "Requirement ID", "Test ID", "Test Name", "Last Status", "Last Run"
        );

        ensureHeaders(TEST_RESULTS_SHEET, "!A1:H1", testResultsHeaders);
        ensureHeaders(TRACEABILITY_SHEET, "!A1:E1", traceabilityHeaders);
    }

    private void ensureHeaders(String sheet, String range, List<Object> headers) throws IOException {
        try {
            List<List<Object>> existing = readSheet(sheet + range);
            if (existing == null || existing.isEmpty()) {
                ValueRange body = new ValueRange().setValues(Collections.singletonList(headers));
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, sheet + range, body)
                        .setValueInputOption("RAW")
                        .execute();
            }
        } catch (Exception e) {
            log.warn("Header initialization failed for {}: {}", sheet, e.getMessage());
        }
    }

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