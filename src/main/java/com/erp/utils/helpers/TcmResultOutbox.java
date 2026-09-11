package com.erp.utils.helpers;

import com.erp.dto.tcm.TcmImportResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/** Durable results for one run; an explicit path must belong to one runner invocation. */
@Slf4j
public final class TcmResultOutbox {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object LOCK = new Object();
    private final String remoteRunId;
    private final Path file;

    public TcmResultOutbox(String remoteRunId) {
        this(remoteRunId, configuredFile(remoteRunId));
    }

    public TcmResultOutbox(String remoteRunId, Path file) {
        if (remoteRunId == null || remoteRunId.isBlank()) {
            throw new IllegalArgumentException("remoteRunId must not be blank");
        }
        this.remoteRunId = remoteRunId;
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    private static Path configuredFile(String id) {
        String configured = System.getProperty("tcm.results.file");
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("remoteRunId must be a valid directory name");
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "tcm-autotest", id, "tcm-results.jsonl");
    }

    public Path resultsFile() { return file; }
    public Path importOkMarker() { return file.getParent().resolve("tcm-import.ok"); }

    /** Clear a stale delivery marker, preserving recoverable results. */
    public void beginRun() {
        synchronized (LOCK) {
            if (Files.exists(importOkMarker()) && !hasImportOk()) {
                try { Files.delete(importOkMarker()); }
                catch (IOException e) {
                    throw new IllegalStateException("Cannot clear stale TCM marker: " + importOkMarker(), e);
                }
            }
        }
    }

    public void append(String testCaseId, String status, Long durationMs, String errorMessage, LocalDateTime executedAt) {
        if (testCaseId == null || testCaseId.isBlank()) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("testCaseId", testCaseId.trim());
        entry.put("status", status != null ? status : "NOT_RUN");
        entry.put("durationMs", durationMs);
        entry.put("errorMessage", errorMessage);
        entry.put("executedAt", (executedAt != null ? executedAt : LocalDateTime.now()).toString());
        synchronized (LOCK) {
            try {
                Files.createDirectories(file.getParent());
                // Runner skips rows without testCaseId, then forwards result rows unchanged to TCM.
                // Keep ownership metadata separate: the TCM result DTO rejects extra properties.
                String rows = MAPPER.writeValueAsString(Map.of("remoteRunId", remoteRunId))
                        + System.lineSeparator() + MAPPER.writeValueAsString(entry) + System.lineSeparator();
                Files.writeString(file, rows,
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("Failed to append TCM outbox {}: {}", file, e.getMessage());
            }
        }
    }

    /** Reject both foreign run IDs and legacy records without an ID. */
    public List<TcmApiClient.BufferedResult> readAll() {
        synchronized (LOCK) {
            if (!Files.isRegularFile(file)) return List.of();
            List<TcmApiClient.BufferedResult> results = new ArrayList<>();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                String entryRunId = null;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonNode entry = MAPPER.readTree(line);
                        if (entry != null && entry.hasNonNull("remoteRunId") && !entry.hasNonNull("testCaseId")) {
                            entryRunId = entry.path("remoteRunId").asText();
                            continue;
                        }
                        String owner = entryRunId;
                        entryRunId = null;
                        if (entry == null || !remoteRunId.equals(owner)) continue;
                        String id = entry.path("testCaseId").asText();
                        if (id.isBlank()) continue;
                        JsonNode duration = entry.get("durationMs");
                        JsonNode error = entry.get("errorMessage");
                        results.add(new TcmApiClient.BufferedResult(id,
                                mapStatus(entry.path("status").asText()),
                                duration != null && duration.isNumber() ? duration.longValue() : null,
                                error != null && !error.isNull() ? error.asText() : null,
                                LocalDateTime.parse(entry.path("executedAt").asText())));
                    } catch (IOException | RuntimeException e) {
                        entryRunId = null;
                        // An interrupted append may leave an incomplete final line.
                        log.warn("Ignoring malformed record in TCM outbox {}", file);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to read TCM outbox {}: {}", file, e.getMessage());
            }
            return results;
        }
    }

    public void writeImportOk(TcmImportResponse response, String source) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(importOkMarker(), "remoteRunId=" + remoteRunId
                        + "\nrunId=" + (response != null ? response.getRunId() : "")
                        + "\nmatched=" + (response != null ? response.getMatched() : 0)
                        + "\nsource=" + (source != null ? source : "LISTENER") + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to write TCM import marker {}: {}", importOkMarker(), e.getMessage());
            }
        }
    }

    public boolean hasImportOk() {
        synchronized (LOCK) {
            try {
                return Files.isRegularFile(importOkMarker()) && Files.readAllLines(importOkMarker(), StandardCharsets.UTF_8)
                        .contains("remoteRunId=" + remoteRunId);
            } catch (IOException e) { return false; }
        }
    }

    private static int mapStatus(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PASS" -> org.testng.ITestResult.SUCCESS;
            case "SKIPPED" -> org.testng.ITestResult.SKIP;
            default -> org.testng.ITestResult.FAILURE;
        };
    }
}
