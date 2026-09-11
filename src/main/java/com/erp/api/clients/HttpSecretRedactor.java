package com.erp.api.clients;

import java.util.regex.Pattern;

/** Redacts diagnostic copies only; never changes the request sent to the server. */
public final class HttpSecretRedactor {
    private static final String KEYS = "authorization|proxy-authorization|cookie|set-cookie|"
            + "x-tcm-api-token|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|"
            + "id[_-]?token|client[_-]?secret|password|passwd|token|secret";
    private static final Pattern JSON_VALUE = Pattern.compile(
            "(?i)(\"(?:" + KEYS + ")\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern QUERY_VALUE = Pattern.compile(
            "(?i)([?&](?:" + KEYS + ")=)[^&\\s]*");
    private static final Pattern LINE_VALUE = Pattern.compile(
            "(?im)(\\b(?:" + KEYS + ")[ \\t]*[:=][ \\t]*)[^\\r\\n]*");
    private static final Pattern COOKIES_BLOCK = Pattern.compile(
            "(?m)^Cookies:[^\\r\\n]*(?:\\R[ \\t]+[^\\r\\n]*)*");

    private HttpSecretRedactor() { }

    public static String redact(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = COOKIES_BLOCK.matcher(text).replaceAll("Cookies: <redacted>");
        sanitized = JSON_VALUE.matcher(sanitized).replaceAll("$1\"<redacted>\"");
        sanitized = QUERY_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
        return LINE_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
    }
}
