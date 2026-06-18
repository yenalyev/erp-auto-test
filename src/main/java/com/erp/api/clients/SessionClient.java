package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.response.Response;
import java.util.Map;
import static io.restassured.RestAssured.given;

public class SessionClient extends BaseClient {

    private static final ObjectMapper MULTIPART_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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

    private Response executeMultipart(
            io.restassured.http.Method method,
            String path,
            Map<String, String> cookies,
            String partName,
            Object jsonPart
    ) {
        try {
            String json = MULTIPART_MAPPER.writeValueAsString(jsonPart);
            return given()
                    .baseUri(ConfigProvider.getBackendUrl())
                    .accept(io.restassured.http.ContentType.JSON)
                    .cookies(cookies != null ? cookies : Map.of())
                    .multiPart(new io.restassured.builder.MultiPartSpecBuilder(json)
                            .controlName(partName)
                            .mimeType("application/json")
                            .charset("UTF-8")
                            .build())
                    .filter(new io.qameta.allure.restassured.AllureRestAssured())
                    .when()
                    .request(method, path)
                    .then()
                    .spec(responseSpec)
                    .extract()
                    .response();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize multipart JSON part", e);
        }
    }
}