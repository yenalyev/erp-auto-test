package com.erp.utils.helpers;

import io.restassured.response.Response;
import lombok.experimental.UtilityClass;
import org.testng.SkipException;

import java.util.List;
import java.util.Objects;

@UtilityClass
public class ApiResponseHelper {

    public static void ensureSuccess(Response response, String action) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(buildMessage(action, status, response));
        }
    }

    public static void ensureJsonBody(Response response, String action) {
        ensureSuccess(response, action);
        String body = response.getBody().asString();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(action + ": empty response body (status=" + response.statusCode() + ")");
        }
        String trimmed = body.stripLeading();
        if (trimmed.startsWith("<")) {
            throw new SkipException(buildMessage(action, response.statusCode(), response));
        }
    }

    public static <T> List<T> parseList(Response response, Class<T> itemClass, String action) {
        ensureJsonBody(response, action);
        List<T> list = DatabaseIntegrityValidator.extractList(response, itemClass);
        if (list == null) {
            return List.of();
        }
        return list.stream().filter(Objects::nonNull).toList();
    }

    private static String buildMessage(String action, int status, Response response) {
        String body = response.getBody().asString();
        String preview = body == null ? "null"
                : body.substring(0, Math.min(300, body.length())).replace('\n', ' ');
        return action + " failed (status=" + status + "). "
                + "Expected JSON API response — got HTML or error. "
                + "Check base.url, SSH/VPN, and Playwright session auth. Body preview: " + preview;
    }
}
