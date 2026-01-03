package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static io.restassured.RestAssured.given;

@Slf4j
public abstract class BaseClient {

    protected RequestSpecification requestSpec;
    protected ResponseSpecification responseSpec;
    protected String authToken;

    public BaseClient(String authToken) {
        this.authToken = authToken;
        this.requestSpec = createRequestSpec();
        this.responseSpec = createResponseSpec();
    }

    /**
     * Create default request specification
     */
    private RequestSpecification createRequestSpec() {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(ConfigProvider.getBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .log(LogDetail.ALL);

        // ✅ Додаємо токен якщо він є
        if (authToken != null && !authToken.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }

        return builder.build();
    }

    /**
     * Create default response specification
     */
    private ResponseSpecification createResponseSpec() {
        return new ResponseSpecBuilder()
                .log(LogDetail.ALL)
                .build();
    }

    /**
     * Update auth token (for token refresh scenarios)
     */
    public void updateAuthToken(String newToken) {
        this.authToken = newToken;
        this.requestSpec = createRequestSpec();
        log.debug("🔄 Auth token updated in client");
    }

    // ✅ Всі методи залишаються такими ж

    protected Response get(String endpoint) {
        log.info("GET request to: {}", endpoint);
        return given()
                .spec(requestSpec)
                .when()
                .get(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response get(String endpoint, Map<String, ?> pathParams) {
        log.info("GET request to: {} with params: {}", endpoint, pathParams);
        return given()
                .spec(requestSpec)
                .pathParams(pathParams)
                .when()
                .get(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response getWithQueryParams(String endpoint, Map<String, ?> queryParams) {
        log.info("GET request to: {} with query params: {}", endpoint, queryParams);
        return given()
                .spec(requestSpec)
                .queryParams(queryParams)
                .when()
                .get(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response post(String endpoint, Object body) {
        log.info("POST request to: {} with body: {}", endpoint, body);
        return given()
                .spec(requestSpec)
                .body(body)
                .when()
                .post(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response post(String endpoint) {
        log.info("POST request to: {}", endpoint);
        return given()
                .spec(requestSpec)
                .when()
                .post(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response put(String endpoint, Object body) {
        log.info("PUT request to: {} with body: {}", endpoint, body);
        return given()
                .spec(requestSpec)
                .body(body)
                .when()
                .put(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response put(String endpoint, Map<String, ?> pathParams, Object body) {
        log.info("PUT request to: {} with params: {} and body: {}", endpoint, pathParams, body);
        return given()
                .spec(requestSpec)
                .pathParams(pathParams)
                .body(body)
                .when()
                .put(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response patch(String endpoint, Object body) {
        log.info("PATCH request to: {} with body: {}", endpoint, body);
        return given()
                .spec(requestSpec)
                .body(body)
                .when()
                .patch(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response delete(String endpoint) {
        log.info("DELETE request to: {}", endpoint);
        return given()
                .spec(requestSpec)
                .when()
                .delete(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }

    protected Response delete(String endpoint, Map<String, ?> pathParams) {
        log.info("DELETE request to: {} with params: {}", endpoint, pathParams);
        return given()
                .spec(requestSpec)
                .pathParams(pathParams)
                .when()
                .delete(endpoint)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }
}