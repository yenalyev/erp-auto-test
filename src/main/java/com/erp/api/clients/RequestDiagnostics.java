package com.erp.api.clients;

import io.restassured.filter.Filter;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Per-thread capture of the last request sent through {@link SessionClient}.
 *
 * <p>RestAssured serialises request bodies internally, so a failed call cannot be reproduced from
 * the {@code Response} alone. That leaves failures whose body is empty — most notably a 400 raised
 * by Spring's {@code HttpMessageNotReadableException} — with no evidence at all. The capturing
 * filter logs the wire-level request, which fixtures then surface in the failure message.
 */
public final class RequestDiagnostics {

    /**
     * Captured requests can be large (multipart, long lists); truncate what we keep so a failing
     * suite does not hold megabytes of diagnostics per thread.
     */
    private static final int MAX_CAPTURED_CHARS = 20_000;

    private static final ThreadLocal<String> LAST_REQUEST = new ThreadLocal<>();

    private RequestDiagnostics() {
    }

    /**
     * Builds a filter that records the outgoing request for the current thread. The returned
     * filter is single-use: create one per request.
     */
    public static Filter capturingFilter() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        RequestLoggingFilter delegate = new RequestLoggingFilter(LogDetail.ALL, stream);
        return (requestSpec, responseSpec, ctx) -> {
            try {
                return delegate.filter(requestSpec, responseSpec, ctx);
            } finally {
                store(buffer.toString(StandardCharsets.UTF_8));
            }
        };
    }

    /** Wire-level dump of the last request on this thread, or {@code null} when nothing captured. */
    public static String lastRequest() {
        return LAST_REQUEST.get();
    }

    public static void clear() {
        LAST_REQUEST.remove();
    }

    private static void store(String captured) {
        if (captured == null || captured.isBlank()) {
            LAST_REQUEST.remove();
            return;
        }
        String sanitized = redactCookies(captured);
        LAST_REQUEST.set(sanitized.length() <= MAX_CAPTURED_CHARS
                ? sanitized
                : sanitized.substring(0, MAX_CAPTURED_CHARS) + "...<truncated>");
    }

    /**
     * Drops the {@code Cookies:} block: it carries live Keycloak session tokens and, being several
     * kilobytes long, would push the request body out of any truncated failure message.
     */
    private static String redactCookies(String captured) {
        StringBuilder result = new StringBuilder(captured.length());
        boolean insideCookies = false;
        for (String line : captured.split("\\R", -1)) {
            if (insideCookies) {
                if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
                insideCookies = false;
            }
            if (line.startsWith("Cookies:")) {
                insideCookies = true;
                result.append("Cookies:\t\t<redacted>").append(System.lineSeparator());
                continue;
            }
            result.append(line).append(System.lineSeparator());
        }
        return result.toString();
    }
}
