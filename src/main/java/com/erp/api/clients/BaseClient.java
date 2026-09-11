package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
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
                .setConfig(HttpClientSupport.config())
                .setBaseUri(ConfigProvider.getBackendUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new SafeHttpDiagnosticsFilter());

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
        return new ResponseSpecBuilder().build();
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
