package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** One sanitized HTTP transcript shared by Allure, logs and fixture diagnostics. */
@Slf4j
public final class SafeHttpDiagnosticsFilter implements Filter {
    private static final String CAPTURED = SafeHttpDiagnosticsFilter.class.getName();
    private static final int MAX_ATTACHMENT_CHARS = 100_000;
    private final boolean attachToAllure;

    public SafeHttpDiagnosticsFilter() {
        this(true);
    }

    public SafeHttpDiagnosticsFilter(boolean attachToAllure) {
        this.attachToAllure = attachToAllure;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        // Global legacy logging and the client filter can occur on the same request.
        if (context.hasValue(CAPTURED)) {
            return context.next(request, responseSpec);
        }
        context.setValue(CAPTURED, true);
        boolean attach = attachToAllure || request.getDefinedFilters().stream()
                .anyMatch(filter -> filter instanceof SafeHttpDiagnosticsFilter safe && safe.attachToAllure);
        RequestDiagnostics.clear();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Response response = null;
        try (PrintStream stream = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            response = new RequestLoggingFilter(LogDetail.ALL, stream).filter(request, responseSpec, context);
            return response;
        } finally {
            String requestText = HttpSecretRedactor.redact(buffer.toString(StandardCharsets.UTF_8));
            RequestDiagnostics.record(requestText);
            publish("HTTP Request", requestText, attach);
            if (!ConfigProvider.verboseLogging()) {
                log.info("{} {}", request.getMethod(), HttpSecretRedactor.redact(request.getURI()));
            }
            if (response != null) {
                String contentType = java.util.Objects.toString(response.getContentType(), "")
                        .toLowerCase(Locale.ROOT);
                boolean textBody = contentType.contains("json") || contentType.startsWith("text/")
                        || contentType.contains("xml") || contentType.contains("x-www-form-urlencoded");
                String responseText = response.statusLine() + "\n" + response.getHeaders() + "\n\n"
                        + (textBody ? response.asString() : "<binary or unspecified response body omitted>");
                publish("HTTP Response", HttpSecretRedactor.redact(responseText), attach);
            }
        }
    }

    private static void publish(String title, String sanitized, boolean attach) {
        String text = sanitized.length() > MAX_ATTACHMENT_CHARS
                ? sanitized.substring(0, MAX_ATTACHMENT_CHARS) + "\n<truncated>" : sanitized;
        if (attach) {
            Allure.addAttachment(title, "text/plain", text, "txt");
        }
        if (ConfigProvider.verboseLogging()) {
            log.info("{}\n{}", title, text);
        }
    }
}
