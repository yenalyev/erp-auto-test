package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.response.Response;
import java.util.Map;
import static io.restassured.RestAssured.given;

public class SessionClient extends BaseClient {

    private final com.erp.fixtures.TestArtifactRegistry artifactRegistry = new com.erp.fixtures.TestArtifactRegistry();

    public com.erp.fixtures.TestArtifactRegistry getArtifactRegistry() {
        return artifactRegistry;
    }

    private static final ObjectMapper MULTIPART_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public SessionClient() {
        super(null); // Токен не потрібен, використовуємо сесії
    }

    /**
     * Спеціальний метод для виконання запитів з куками сесії
     */
    public Response executeWithCookies(
            io.restassured.http.Method method,
            String path,
            Object body,
            Map<String, String> cookies
    ) {
        return executeWithCookies(method, path, body, cookies, Map.of());
    }

    public Response executeWithCookies(
            io.restassured.http.Method method,
            String path,
            Object body,
            Map<String, String> cookies,
            Map<String, ?> queryParams
    ) {
        var requestBuilder = given()
                .spec(requestSpec)
                .cookies(cookies != null ? cookies : Map.of());

        if (queryParams != null && !queryParams.isEmpty()) {
            requestBuilder = requestBuilder.queryParams(queryParams);
        }

        if (body != null) {
            requestBuilder = requestBuilder.body(body);
        }

        return requestBuilder
                .when()
                .request(method, path)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    /**
     * Multipart POST (e.g. {@code POST /relocations/receive} with JSON part {@code request}).
     */
    public Response executeMultipartPost(
            String path,
            Map<String, String> cookies,
            String partName,
            Object jsonPart
    ) {
        return executeMultipart(io.restassured.http.Method.POST, path, cookies, partName, jsonPart);
    }

    /**
     * Multipart PUT (e.g. {@code PUT /relocations/{id}/receive}).
     */
    public Response executeMultipartPut(
            String path,
            Map<String, String> cookies,
            String partName,
            Object jsonPart
    ) {
        return executeMultipart(io.restassured.http.Method.PUT, path, cookies, partName, jsonPart);
    }

    /**
     * Multipart POST with a binary file part (e.g. {@code POST /resources/{id}/image}, part {@code file}).
     */
    public Response executeMultipartFilePost(
            String path,
            Map<String, String> cookies,
            String partName,
            byte[] content,
            String filename,
            String mimeType
    ) {
        return given()
                .config(HttpClientSupport.config())
                .baseUri(ConfigProvider.getBackendUrl())
                .accept(io.restassured.http.ContentType.ANY)
                .cookies(cookies != null ? cookies : Map.of())
                .multiPart(partName, filename, content, mimeType)
                .filter(new SafeHttpDiagnosticsFilter())
                .when()
                .post(path)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    private Response executeMultipart(
            io.restassured.http.Method method,
            String path,
            Map<String, String> cookies,
            String partName,
            Object jsonPart
    ) {
        String json;
        try {
            json = MULTIPART_MAPPER.writeValueAsString(jsonPart);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize multipart JSON part", e);
        }
        return given()
                .config(HttpClientSupport.config())
                .baseUri(ConfigProvider.getBackendUrl())
                .accept(io.restassured.http.ContentType.JSON)
                .cookies(cookies != null ? cookies : Map.of())
                .multiPart(new io.restassured.builder.MultiPartSpecBuilder(json)
                        .controlName(partName)
                        .mimeType("application/json")
                        .charset("UTF-8")
                        .build())
                .filter(new SafeHttpDiagnosticsFilter())
                .when()
                .request(method, path)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }
}
