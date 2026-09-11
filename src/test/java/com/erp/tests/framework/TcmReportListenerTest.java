package com.erp.tests.framework;

import com.erp.annotations.TestCaseId;
import com.erp.listeners.TcmReportListener;
import com.erp.utils.helpers.TcmResultOutbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.testng.ITestContext;
import org.testng.Reporter;
import org.testng.annotations.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TcmReportListenerTest {
    private final List<JsonNode> imports = Collections.synchronizedList(new ArrayList<>());
    private HttpServer server;
    private FrameworkProperties properties;
    private Path outboxFile;

    @BeforeClass
    public void startLocalTcm() throws Exception {
        Path root = Path.of("target", "framework-tests");
        Files.createDirectories(root);
        outboxFile = Files.createTempDirectory(root, "listener-").resolve("results.jsonl");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/autotest/runs", exchange -> {
            imports.add(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = "{\"runId\":1,\"matched\":1,\"unmatched\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Map<String, String> overrides = new HashMap<>(Map.of("env", "framework", "tcm.enabled", "true",
                "tcm.base.url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "tcm.api.token", "synthetic-local-token", "tcm.test.plan.id", "1", "logging.verbose", "false",
                "tcm.remote.run.id", "", "tcm.results.file", outboxFile.toString(),
                "tcm.feature.id", "", "tcm.ac.id", ""));
        overrides.put("google.sheets.enabled", "false");
        properties = new FrameworkProperties(overrides);
        RestAssured.reset();
    }

    @AfterClass(alwaysRun = true)
    public void stopLocalTcm() {
        if (server != null) server.stop(0);
        RestAssured.reset();
        if (properties != null) properties.close();
    }

    @Test
    public void emptyNewSuiteDoesNotImportOldPass(ITestContext context) {
        imports.clear();
        TcmResultOutbox old = new TcmResultOutbox("old-run", outboxFile);
        old.append("OLD-PASS", "PASS", 1L, null, LocalDateTime.of(2020, 1, 1, 0, 0));
        TcmReportListener listener = new TcmReportListener();
        listener.onStart(context.getSuite());
        listener.onFinish(context.getSuite());
        assertThat(imports).isEmpty();
        assertThat(old.readAll()).isNotEmpty();
    }

    @Test
    @TestCaseId("FRAMEWORK-TCM-CURRENT")
    public void sequentialSuitesUseDistinctIdsAndOnlyCurrentResults(ITestContext context) {
        imports.clear();
        TcmReportListener listener = new TcmReportListener();
        for (int i = 0; i < 2; i++) {
            listener.onStart(context.getSuite());
            listener.onTestStart(Reporter.getCurrentTestResult());
            listener.onTestSuccess(Reporter.getCurrentTestResult());
            listener.onFinish(context.getSuite());
        }
        assertThat(imports).hasSize(2);
        assertThat(imports.get(0).path("remoteRunId").asText())
                .isNotEqualTo(imports.get(1).path("remoteRunId").asText());
        for (JsonNode request : imports) {
            assertThat(request.path("results").size()).isEqualTo(1);
            assertThat(request.path("results").get(0).path("testCaseId").asText()).isEqualTo("FRAMEWORK-TCM-CURRENT");
        }
    }

    @Test
    @TestCaseId("FRAMEWORK-TCM-RUNNER")
    public void preservesExplicitRunnerId(ITestContext context) {
        imports.clear();
        System.setProperty("tcm.remote.run.id", "runner-invocation");
        try {
            TcmReportListener listener = new TcmReportListener();
            listener.onStart(context.getSuite());
            listener.onTestSuccess(Reporter.getCurrentTestResult());
            listener.onFinish(context.getSuite());
            assertThat(imports).singleElement().satisfies(request ->
                    assertThat(request.path("remoteRunId").asText()).isEqualTo("runner-invocation"));
            assertThat(new TcmResultOutbox("runner-invocation", outboxFile).hasImportOk()).isTrue();
        } finally {
            System.setProperty("tcm.remote.run.id", "");
        }
    }

    @Test
    public void scopedEmptySuiteStillFailsEvenWithForeignOutbox(ITestContext context) {
        imports.clear();
        new TcmResultOutbox("foreign", outboxFile).append("OLD", "PASS", 1L, null, LocalDateTime.now());
        System.setProperty("tcm.feature.id", "7");
        try {
            TcmReportListener listener = new TcmReportListener();
            listener.onStart(context.getSuite());
            assertThatThrownBy(() -> listener.onFinish(context.getSuite()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Жоден тест не виконано");
            assertThat(imports).isEmpty();
        } finally {
            System.setProperty("tcm.feature.id", "");
        }
    }
}
