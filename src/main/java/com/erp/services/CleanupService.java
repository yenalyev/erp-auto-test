package com.erp.services;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanupService {
    private final String baseUrl;

    public CleanupService(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void deleteItem(String itemId, String token) {
        Response response = RestAssured.given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + token)
                .delete("/api/items/" + itemId);

        if (response.statusCode() != 204 && response.statusCode() != 200) {
            log.warn("Failed to delete item {}: {}", itemId, response.statusLine());
        }
    }

    public void deleteOrder(String orderId, String token) {
        Response response = RestAssured.given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + token)
                .delete("/api/orders/" + orderId);

        if (response.statusCode() != 204 && response.statusCode() != 200) {
            log.warn("Failed to delete order {}: {}", orderId, response.statusLine());
        }
    }
}
