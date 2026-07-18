package com.erp.api.clients;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentCreateRequest;
import com.erp.models.request.EquipmentRelocationReceiveEditRequest;
import com.erp.models.request.RelocationIncidentRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationUpdateRequest;
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
            Object... pathParams
    ) {
        Map<String, String> sessionCookies = getSessionForRole(role);

        String path = (pathParams != null && pathParams.length > 0)
                ? endpoint.getPath(pathParams)
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
        return execute(endpoint, role, null, new Object[0]);
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, Object body) {
        return execute(endpoint, role, body, new Object[0]);
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, Object body, Object pathParam) {
        return execute(endpoint, role, body, new Object[]{pathParam});
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, Object body, Object firstParam, Object secondParam) {
        return execute(endpoint, role, body, new Object[]{firstParam, secondParam});
    }

    public Response execute(ApiEndpointDefinition endpoint, UserRole role, String pathParam) {
        return execute(endpoint, role, null, new Object[]{pathParam});
    }

    @Step("API Request: {endpoint} as {role} with query params")
    public Response executeWithQueryParams(
            ApiEndpointDefinition endpoint,
            UserRole role,
            Map<String, ?> queryParams,
            Object... pathParams
    ) {
        Map<String, String> sessionCookies = getSessionForRole(role);

        String path = (pathParams != null && pathParams.length > 0)
                ? endpoint.getPath(pathParams)
                : endpoint.getPath();

        log.debug("Executing {} {} with query {} (Role: {})",
                endpoint.getHttpMethod(), path, queryParams, role);

        return apiClient.executeWithCookies(
                endpoint.getHttpMethod(),
                path,
                null,
                sessionCookies,
                queryParams != null ? queryParams : Map.of()
        );
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

    @Step("API Request: POST /relocations/receive as {role}")
    public Response executeRelocationReceive(RelocationInputRequest request, UserRole role) {
        Map<String, String> sessionCookies = getSessionForRole(role);
        return apiClient.executeMultipartPost(
                ApiEndpointDefinition.RELOCATION_POST_RECEIVE.getPath(),
                sessionCookies,
                "request",
                request
        );
    }

    @Step("API Request: PUT /relocations/{id}/receive as {role}")
    public Response executeRelocationUpdateReceive(Long relocationId,
                                                   Long storageId,
                                                   RelocationInputEditRequest request,
                                                   UserRole role) {
        Map<String, String> sessionCookies = getSessionForRole(role);
        String path = ApiEndpointDefinition.RELOCATION_PUT_UPDATE_RECEIVE.getPath(
                relocationId, storageId);
        return apiClient.executeMultipartPut(path, sessionCookies, "request", request);
    }

    @Step("API Request: PUT /relocations/{id}/resolve as {role}")
    public Response executeRelocationResolve(Long relocationId,
                                             Long storageId,
                                             RelocationUpdateRequest request,
                                             UserRole role) {
        return execute(
                ApiEndpointDefinition.RELOCATION_PUT_RESOLVE,
                role,
                request,
                relocationId,
                storageId);
    }

    @Step("API Request: DELETE /relocations/{id} as {role}")
    public Response executeRelocationDelete(Long relocationId, Long storageId, UserRole role) {
        return execute(
                ApiEndpointDefinition.RELOCATION_DELETE,
                role,
                null,
                relocationId,
                storageId);
    }

    @Step("API Request: PUT /relocations/equipment/{id}/receive as {role}")
    public Response executeEquipmentRelocationUpdateReceive(Long relocationId,
                                                            Long storageId,
                                                            EquipmentRelocationReceiveEditRequest request,
                                                            UserRole role) {
        Map<String, String> sessionCookies = getSessionForRole(role);
        String path = ApiEndpointDefinition.EQUIPMENT_RELOCATION_PUT_UPDATE_RECEIVE.getPath(
                relocationId, storageId);
        return apiClient.executeMultipartPut(path, sessionCookies, "request", request);
    }

    @Step("API Request: POST /equipment as {role}")
    public Response executeEquipmentCreate(EquipmentCreateRequest request, UserRole role) {
        Map<String, String> sessionCookies = getSessionForRole(role);
        return apiClient.executeMultipartPost(
                ApiEndpointDefinition.EQUIPMENT_POST_CREATE.getPath(),
                sessionCookies,
                "request",
                request);
    }

    @Step("API Request: POST /incidents/relocations as {role}")
    public Response executeIncidentCreate(RelocationIncidentRequest request, UserRole role) {
        Map<String, String> sessionCookies = getSessionForRole(role);
        return apiClient.executeMultipartPost(
                ApiEndpointDefinition.INCIDENT_POST_CREATE.getPath(),
                sessionCookies,
                "request",
                request);
    }
}