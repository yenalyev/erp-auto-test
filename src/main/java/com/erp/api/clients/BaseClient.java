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

/**
 * Base client for all API requests
 * Provides common HTTP methods and request/response specifications
 */
@Slf4j
public abstract class BaseClient {

    protected RequestSpecification requestSpec;
    protected ResponseSpecification responseSpec;

    public BaseClient() {
        this.requestSpec = createRequestSpec();
        this.responseSpec = createResponseSpec();
    }

    /**
     * Create default request specification
     */
    private RequestSpecification createRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigProvider.getBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader("Authorization", "Bearer " + ConfigProvider.getAuthToken())
                .addFilter(new AllureRestAssured())
                .log(LogDetail.ALL)
                .build();
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
     * Execute GET request
     */
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

    /**
     * Execute GET request with path parameters
     */
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

    /**
     * Execute GET request with query parameters
     */
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

    /**
     * Execute POST request with body
     */
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

    /**
     * Execute POST request without body
     */
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

    /**
     * Execute PUT request with body
     */
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

    /**
     * Execute PUT request with path parameters
     */
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

    /**
     * Execute PATCH request with body
     */
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

    /**
     * Execute DELETE request
     */
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

    /**
     * Execute DELETE request with path parameters
     */
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