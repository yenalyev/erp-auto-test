package com.erp.tests.framework;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.erp.api.clients.*;
import com.erp.utils.config.ConfigProvider;
import com.sun.net.httpserver.HttpServer;
import io.qameta.allure.*;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import io.restassured.RestAssured;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

public class HttpClientInfrastructureTest {
    private static final String COOKIE = "synthetic-cookie-73x";
    private static final String AUTH = "synthetic-auth-84x";
    private static final String PASSWORD = "synthetic-password-95x";
    private static final String RESPONSE_TOKEN = "synthetic-response-16x";
    private HttpServer server;
    private ExecutorService executor;
    private FrameworkProperties properties;
    private final Queue<WireRequest> requests = new ConcurrentLinkedQueue<>();

    private record WireRequest(String method, String contentType, String cookie, String auth, String body) { }

    @BeforeClass
    public void startLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new WireRequest(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Cookie"), exchange.getRequestHeaders().getFirst("Authorization"), body));
            try {
                if (exchange.getRequestURI().getPath().equals("/slow")) Thread.sleep(1700);
                byte[] response = ("{\"access_token\":\"" + RESPONSE_TOKEN + "\",\"value\":\"kept\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Set-Cookie", "SESSION=" + RESPONSE_TOKEN + "; HttpOnly");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // A client timeout deliberately closes its connection.
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        properties = new FrameworkProperties(Map.of("env", "framework", "backend.url", url, "base.url", url,
                "api.timeout", "1", "logging.verbose", "false", "tcm.enabled", "false", "google.sheets.enabled", "false"));
        RestAssured.reset();
    }

    @AfterClass(alwaysRun = true)
    public void stopLocalServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        RestAssured.reset();
        RequestDiagnostics.clear();
        if (properties != null) properties.close();
    }

    @DataProvider(name = "verbose")
    public Object[][] verbose() { return new Object[][]{{false}, {true}}; }

    @Test(dataProvider = "verbose")
    public void redactsArtifactsAndLogsWithoutChangingTheWire(boolean verbose) {
        System.setProperty("logging.verbose", String.valueOf(verbose));
        ConfigProvider.reload();
        requests.clear();
        Map<String, String> attachments = new ConcurrentHashMap<>();
        AllureLifecycle previous = Allure.getLifecycle();
        Logger logger = (Logger) LoggerFactory.getLogger(SafeHttpDiagnosticsFilter.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        AllureLifecycle lifecycle = new AllureLifecycle(new AllureResultsWriter() {
            public void write(TestResult result) { }
            public void write(TestResultContainer container) { }
            public void write(String source, InputStream attachment) {
                try { attachments.put(source, new String(attachment.readAllBytes(), StandardCharsets.UTF_8)); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        });
        String uuid = UUID.randomUUID().toString();
        try {
            Allure.setLifecycle(lifecycle);
            lifecycle.scheduleTestCase(new TestResult().setUuid(uuid).setName("local HTTP redaction"));
            lifecycle.startTestCase(uuid);
            RestAssured.replaceFiltersWith(new SafeHttpDiagnosticsFilter(false));
            HeaderSessionClient client = new HeaderSessionClient();
            Response response = client.executeWithCookies(Method.POST, "/echo?access_token=" + PASSWORD,
                    Map.of("password", PASSWORD, "nested", Map.of("client_secret", PASSWORD), "value", "kept"),
                    Map.of("SESSION", COOKIE));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("access_token")).isEqualTo(RESPONSE_TOKEN);
            WireRequest wire = requests.element();
            assertThat(wire.cookie()).contains(COOKIE);
            assertThat(wire.auth()).contains(AUTH);
            assertThat(wire.body()).contains(PASSWORD);
            assertThat(attachments).hasSize(2); // Global + client filters must not duplicate attachments.
            String reports = String.join("\n", attachments.values()) + RequestDiagnostics.lastRequest();
            assertThat(reports).contains("kept", "<redacted>").doesNotContain(COOKIE, AUTH, PASSWORD, RESPONSE_TOKEN);
            String logText = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logText).isNotBlank().doesNotContain(COOKIE, AUTH, PASSWORD, RESPONSE_TOKEN);
        } finally {
            lifecycle.stopTestCase(uuid);
            Allure.setLifecycle(previous);
            logger.detachAppender(logs);
            logs.stop();
            RestAssured.reset();
        }
    }

    @DataProvider(name = "requestKinds")
    public Object[][] requestKinds() { return new Object[][]{{"JSON"}, {"POST"}, {"PUT"}, {"FILE"}}; }

    @Test(dataProvider = "requestKinds")
    public void preservesJsonAndMultipartPayloads(String kind) {
        requests.clear();
        Response response = send(kind, "/echo");
        assertThat(response.statusCode()).isEqualTo(200);
        WireRequest wire = requests.element();
        assertThat(wire.cookie()).contains(COOKIE);
        assertThat(wire.method()).isEqualTo(kind.equals("PUT") ? "PUT" : "POST");
        assertThat(wire.contentType()).startsWith(kind.equals("JSON") ? "application/json" : "multipart/form-data");
        assertThat(wire.body()).contains("payload-kept");
        if (!kind.equals("FILE")) assertThat(wire.body()).contains(PASSWORD);
        assertThat(RequestDiagnostics.lastRequest()).doesNotContain(COOKIE, PASSWORD);
    }

    @Test(dataProvider = "requestKinds")
    public void allRequestTypesRespectTheConfiguredTimeout(String kind) {
        long start = System.nanoTime();
        Throwable thrown = catchThrowable(() -> send(kind, "/slow"));
        assertThat(thrown).as("Expected timeout for %s", kind).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) root = root.getCause();
        assertThat(root).isInstanceOf(SocketTimeoutException.class);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5000);
        assertThat(RequestDiagnostics.lastRequest()).contains("/slow").doesNotContain(COOKIE);
        assertThat(thrown.getMessage()).doesNotContain("serialize multipart");
    }

    @Test
    public void masksEscapedJsonAndCaseInsensitiveHeaders() {
        String diagnostic = "Authorization: Bearer wire-auth\nSet-Cookie: SESSION=wire-cookie\n"
                + "{\"PASSWORD\":\"quote\\\"secret\",\"refreshToken\":\"refresh-secret\",\"safe\":\"ok\"}";
        assertThat(HttpSecretRedactor.redact(diagnostic)).contains("\"safe\":\"ok\"")
                .doesNotContain("wire-auth", "wire-cookie", "secret");
    }

    private Response send(String kind, String path) {
        SessionClient client = new SessionClient();
        Map<String, String> cookies = Map.of("SESSION", COOKIE);
        return switch (kind) {
            case "JSON" -> client.executeWithCookies(Method.POST, path, Map.of("value", "payload-kept", "password", PASSWORD), cookies);
            case "POST" -> client.executeMultipartPost(path, cookies, "request", Map.of("value", "payload-kept", "password", PASSWORD));
            case "PUT" -> client.executeMultipartPut(path, cookies, "request", Map.of("value", "payload-kept", "password", PASSWORD));
            case "FILE" -> client.executeMultipartFilePost(path, cookies, "file",
                    "payload-kept".getBytes(StandardCharsets.UTF_8), "sample.txt", "text/plain");
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static class HeaderSessionClient extends SessionClient {
        HeaderSessionClient() { requestSpec.header("Authorization", "Bearer " + AUTH); }
    }
}
