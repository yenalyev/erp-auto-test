package com.erp.api.clients;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.utils.auth.AuthService;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class ApiExecutor {

    // Кеш сесій (Thread-safe)
    private final Map<UserRole, Map<String, String>> roleSessionCache = new ConcurrentHashMap<>();

    private final SessionClient apiClient;
    private final AuthService authService;

    /**
     * ✅ Головний публічний метод виконання запиту
     */
    @Step("API Request: {endpoint} as {role}")
    public Response execute(
            ApiEndpointDefinition endpoint,
            UserRole role,
            Object requestBody,
            String pathParam
    ) {
        Map<String, String> sessionCookies = getSessionForRole(role);

        String path = (pathParam != null)
                ? endpoint.getPath(pathParam)
                : endpoint.getPath();

        log.debug("Executing {} {} (Role: {})", endpoint.getHttpMethod(), path, role);

        return apiClient.executeWithCookies(
                endpoint.getHttpMethod(),
                path,
                requestBody,
                sessionCookies
        );
    }

    // --- Зручні перевантаження (Overloads) ---

    public Response execute(ApiEndpointDefinition endpoint, UserRole role) {
        return execute(endpoint, role, null, null);
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, Object body) {
        return execute(endpoint, role, body, null);
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, String pathParam) {
        return execute(endpoint, role, null, pathParam);
    }

    /**
     * Логіка отримання/кешування сесії
     */
    protected Map<String, String> getSessionForRole(UserRole role) {
        if (role == UserRole.ANONYMOUS) {
            return new HashMap<>();
        }

        return roleSessionCache.computeIfAbsent(role, r -> {
            log.info("🔐 Authenticating and caching session for: {}", role);
            return authService.getSessionForUser(role.getUsername(), role.getPassword());
        });
    }

    /**
     * Метод для примусового очищення кешу (наприклад, після тестів зміни пароля)
     */
    public void clearSessionCache() {
        roleSessionCache.clear();
        log.debug("🧹 Session cache cleared");
    }
}