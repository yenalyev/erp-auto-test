package com.erp.utils.helpers;

import io.restassured.response.Response;
import java.util.function.Supplier;

/** An unavailable API is a failure; only an explicitly disabled integration is optional. */
public final class IntegrationPrerequisites {
    private IntegrationPrerequisites() {}

    public static boolean probe(boolean enabled, String name, Supplier<Response> request) {
        if (!enabled) return false;
        Response response = request.get();
        ApiResponseHelper.ensureJsonBody(response, name + " pre-flight");
        if (response.statusCode() != 200) {
            throw new IllegalStateException(name + " pre-flight expected HTTP 200, got " + response.statusCode());
        }
        // Ensure the body really is JSON, rather than merely not starting with '<'.
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.asString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(name + " pre-flight returned invalid JSON", e);
        }
        return true;
    }
}
