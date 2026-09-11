package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

/** Transport settings shared by JSON, multipart and legacy requests. */
public final class HttpClientSupport {
    private HttpClientSupport() { }

    public static RestAssuredConfig config() {
        int timeoutMs = Math.multiplyExact(ConfigProvider.getTimeout(), 1000);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("api.timeout must be positive");
        }
        return RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeoutMs)
                .setParam("http.socket.timeout", timeoutMs)
                .setParam("http.connection-manager.timeout", (long) timeoutMs));
    }
}
