package com.erp.tests.framework;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.utils.helpers.TcmResultOutbox;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class TcmResultOutboxTest {
    private Path tempFile() throws Exception {
        Path root = Path.of("target", "framework-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "outbox-").resolve("results.jsonl");
    }

    @Test
    public void defaultPathsAreIsolatedByRunId() {
        String previous = System.getProperty("tcm.results.file");
        System.clearProperty("tcm.results.file");
        try {
            TcmResultOutbox first = new TcmResultOutbox("first-run");
            TcmResultOutbox second = new TcmResultOutbox("second-run");
            assertThat(first.resultsFile()).isNotEqualTo(second.resultsFile());
            assertThat(first.resultsFile().getParent().getFileName().toString()).isEqualTo("first-run");
            assertThat(second.resultsFile().getParent().getFileName().toString()).isEqualTo("second-run");
        } finally {
            if (previous != null) System.setProperty("tcm.results.file", previous);
        }
    }

    @Test
    public void filtersForeignAndLegacyResultsWhenAnExplicitPathIsReused() throws Exception {
        Path file = tempFile();
        TcmResultOutbox old = new TcmResultOutbox("old-run", file);
        old.append("OLD", "PASS", 3L, null, LocalDateTime.of(2020, 1, 1, 0, 0));
        Files.writeString(file, "{\"testCaseId\":\"LEGACY\",\"status\":\"PASS\"}\n", StandardOpenOption.APPEND);
        TcmResultOutbox current = new TcmResultOutbox("new-run", file);
        assertThat(current.readAll()).isEmpty();
        current.append("NEW", "FAIL", 7L, "current error", LocalDateTime.now());
        assertThat(current.readAll()).singleElement().satisfies(result -> {
            assertThat(result.testCaseId()).isEqualTo("NEW");
            assertThat(result.testngStatus()).isEqualTo(org.testng.ITestResult.FAILURE);
        });
        assertThat(old.readAll()).singleElement().satisfies(result ->
                assertThat(result.testCaseId()).isEqualTo("OLD"));
    }

    @Test
    public void roundTripsControlCharactersAndRecoversCompleteRecords() throws Exception {
        Path file = tempFile();
        TcmResultOutbox outbox = new TcmResultOutbox("run", file);
        String error = "line 1\n\t\"quote\"\\path\r\u0001 українська";
        LocalDateTime time = LocalDateTime.of(2026, 9, 11, 10, 0, 1);
        outbox.append("TEST", "FAIL", 42L, error, time);
        Files.writeString(file, "{incomplete", StandardOpenOption.APPEND);
        assertThat(outbox.readAll()).singleElement().satisfies(result -> {
            assertThat(result.errorMessage()).isEqualTo(error);
            assertThat(result.executedAt()).isEqualTo(time);
            assertThat(result.durationMs()).isEqualTo(42L);
        });
        assertThat(Files.readAllLines(file)).hasSize(3);
    }

    @Test
    public void deliveryMarkerBelongsToItsRun() throws Exception {
        Path file = tempFile();
        TcmResultOutbox old = new TcmResultOutbox("old-run", file);
        old.append("OLD", "PASS", 1L, null, LocalDateTime.now());
        old.writeImportOk(TcmImportResponse.builder().runId(12L).matched(1).build(), "LISTENER");
        assertThat(old.hasImportOk()).isTrue();
        TcmResultOutbox current = new TcmResultOutbox("new-run", file);
        assertThat(current.hasImportOk()).isFalse();
        current.beginRun();
        assertThat(Files.exists(current.importOkMarker())).isFalse();
        assertThat(old.readAll()).hasSize(1);
        current.writeImportOk(TcmImportResponse.builder().runId(13L).matched(1).build(), "LISTENER");
        assertThat(current.hasImportOk()).isTrue();
        assertThat(old.hasImportOk()).isFalse();
    }

    @Test
    public void runnerCanForwardResultRowsToStrictTcmDto() throws Exception {
        Path file = tempFile();
        TcmResultOutbox outbox = new TcmResultOutbox("runner-compatible", file);
        outbox.append("CASE", "PASS", 12L, null, LocalDateTime.now());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var forwarded = new java.util.ArrayList<com.erp.dto.tcm.TcmResultDto>();
        for (String line : Files.readAllLines(file)) {
            var node = mapper.readTree(line);
            // Matches the runner's readResults contract: metadata rows are not submitted.
            if (node.hasNonNull("testCaseId")) {
                forwarded.add(mapper.treeToValue(node, com.erp.dto.tcm.TcmResultDto.class));
            }
        }
        assertThat(forwarded).singleElement().satisfies(result -> {
            assertThat(result.getTestCaseId()).isEqualTo("CASE");
            assertThat(result.getStatus()).isEqualTo("PASS");
        });
        assertThat(outbox.readAll()).hasSize(1);
    }

    @Test
    public void concurrentAppendsProduceCompleteRecords() throws Exception {
        TcmResultOutbox outbox = new TcmResultOutbox("concurrent", tempFile());
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 40; i++) {
                int id = i;
                futures.add(executor.submit(() -> outbox.append("CASE-" + id, "PASS", 1L, null, LocalDateTime.now())));
            }
            for (Future<?> future : futures) future.get();
        }
        assertThat(outbox.readAll()).hasSize(40);
        assertThat(outbox.readAll().stream().map(result -> result.testCaseId()).distinct().count()).isEqualTo(40L);
    }
}
