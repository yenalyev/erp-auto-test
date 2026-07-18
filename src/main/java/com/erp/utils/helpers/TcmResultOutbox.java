package com.erp.utils.helpers;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.utils.config.ConfigProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Durable JSONL outbox for TCM results. Survives listener/network failures so the runner can fallback-ship.
 */
@Slf4j
public final class TcmResultOutbox {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Object LOCK = new Object();

    private TcmResultOutbox() {
    }

    public static Path resultsFile() {
        String configured = System.getProperty("tcm.results.file");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        String remoteRunId = ConfigProvider.getTcmRemoteRunId();
        String dirName = remoteRunId != null ? remoteRunId : "local";
        return Path.of(System.getProperty("java.io.tmpdir"), "tcm-autotest", dirName, "tcm-results.jsonl");
    }

    public static Path importOkMarker() {
        return resultsFile().getParent().resolve("tcm-import.ok");
    }

    public static void append(String testCaseId, String status, Long durationMs, String errorMessage, LocalDateTime executedAt) {
        if (testCaseId == null || testCaseId.isBlank()) {
            return;
        }
        Path file = resultsFile();
        String escapedError = escapeJson(errorMessage);
        String line = String.format(Locale.ROOT,
                "{\"testCaseId\":\"%s\",\"status\":\"%s\",\"durationMs\":%s,\"errorMessage\":%s,\"executedAt\":\"%s\"}%n",
                escapeJson(testCaseId.trim()),
                escapeJson(status != null ? status : "NOT_RUN"),
                durationMs != null ? durationMs : "null",
                escapedError == null ? "null" : "\"" + escapedError + "\"",
                executedAt != null ? executedAt.format(ISO) : LocalDateTime.now().format(ISO));
        synchronized (LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                log.warn("Failed to append TCM outbox {}: {}", file, ex.getMessage());
            }
        }
    }

    public static List<TcmApiClient.BufferedResult> readAll() {
        Path file = resultsFile();
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<TcmApiClient.BufferedResult> results = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String testCaseId = extractJsonString(line, "testCaseId");
                if (testCaseId == null || testCaseId.isBlank()) {
                    continue;
                }
                String status = extractJsonString(line, "status");
                Long durationMs = extractJsonLong(line, "durationMs");
                String errorMessage = extractJsonString(line, "errorMessage");
                String executedAtRaw = extractJsonString(line, "executedAt");
                LocalDateTime executedAt = LocalDateTime.now();
                if (executedAtRaw != null && !executedAtRaw.isBlank()) {
                    try {
                        executedAt = LocalDateTime.parse(executedAtRaw);
                    } catch (Exception ignored) {
                        // keep now
                    }
                }
                results.add(new TcmApiClient.BufferedResult(
                        testCaseId,
                        mapStatusToTestng(status),
                        durationMs,
                        errorMessage,
                        executedAt));
            }
        } catch (IOException ex) {
            log.warn("Failed to read TCM outbox {}: {}", file, ex.getMessage());
        }
        return results;
    }

    public static void writeImportOk(TcmImportResponse response, String source) {
        Path marker = importOkMarker();
        try {
            Files.createDirectories(marker.getParent());
            String body = "runId=" + (response != null ? response.getRunId() : "")
                    + "\nmatched=" + (response != null ? response.getMatched() : 0)
                    + "\nsource=" + (source != null ? source : "LISTENER")
                    + "\n";
            Files.writeString(marker, body, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to write TCM import OK marker {}: {}", marker, ex.getMessage());
        }
    }

    public static boolean hasImportOk() {
        return Files.isRegularFile(importOkMarker());
    }

    private static int mapStatusToTestng(String status) {
        if (status == null) {
            return org.testng.ITestResult.FAILURE;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PASS" -> org.testng.ITestResult.SUCCESS;
            case "SKIPPED" -> org.testng.ITestResult.SKIP;
            default -> org.testng.ITestResult.FAILURE;
        };
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String extractJsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        if (json.startsWith("null", i)) {
            return null;
        }
        if (json.charAt(i) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static Long extractJsonLong(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.startsWith("null", i)) {
            return null;
        }
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            i++;
        }
        if (start == i) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(start, i));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
