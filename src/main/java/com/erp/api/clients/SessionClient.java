package com.erp.api.clients;

import io.restassured.response.Response;
import java.util.Map;
import static io.restassured.RestAssured.given;

public class SessionClient extends BaseClient {

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
        return given()
                .spec(requestSpec)
                .cookies(cookies)
                .body(body != null ? body : "")
                .when()
                .request(method, path)
                .then()
                .spec(responseSpec)
                .extract()
                .response();
    }
}