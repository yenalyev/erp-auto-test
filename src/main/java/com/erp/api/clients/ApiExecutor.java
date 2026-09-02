package com.erp.api.clients;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.EquipmentCreateRequest;
import com.erp.models.request.EquipmentRelocationReceiveEditRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.RelocationIncidentRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.utils.auth.AuthService;
import com.erp.utils.auth.SessionUnauthorizedRetry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class ApiExecutor {

    // Кеш сесій (Thread-safe)
    private final Map<UserRole, Map<String, String>> roleSessionCache = new ConcurrentHashMap<>();
    /** {@link #setSessionForRole} bindings — 401 retry must re-login the same Keycloak user. */
    private final Map<UserRole, RoleCredentials> roleCredentials = new ConcurrentHashMap<>();

    private final SessionClient apiClient;
    private final AuthService authService;

    private record RoleCredentials(String username, String password) {
    }

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
        String path = (pathParams != null && pathParams.length > 0)
                ? endpoint.getPath(pathParams)
                : endpoint.getPath();

        log.debug("Executing {} {} (Role: {})", endpoint.getHttpMethod(), path, role);

        return executeWithSessionRetry(role, cookies -> apiClient.executeWithCookies(
                endpoint.getHttpMethod(),
                path,
                requestBody,
                cookies
        ));
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
        String path = (pathParams != null && pathParams.length > 0)
                ? endpoint.getPath(pathParams)
                : endpoint.getPath();

        log.debug("Executing {} {} with query {} (Role: {})",
                endpoint.getHttpMethod(), path, queryParams, role);

        return executeWithSessionRetry(role, cookies -> apiClient.executeWithCookies(
                endpoint.getHttpMethod(),
                path,
                null,
                cookies,
                queryParams != null ? queryParams : Map.of()
        ));
    }

    /**
     * Логіка отримання/кешування сесії
     */
    protected Map<String, String> getSessionForRole(UserRole role) {
        if (role == UserRole.ANONYMOUS) {
            return new HashMap<>();
        }

        return roleSessionCache.computeIfAbsent(role, r -> {
            RoleCredentials creds = credentialsFor(r);
            log.info("🔐 Authenticating and caching session for: {} ({})", r, creds.username());
            return authService.getSessionForUser(creds.username(), creds.password());
        });
    }

    /**
     * Point a {@link UserRole} at another Keycloak user for this executor instance
     * (ephemeral restricted owner). Does not change the real role credentials.
     */
    public void setSessionForRole(UserRole role, String username, String password) {
        log.info("🔐 Binding role {} to Keycloak user {}", role, username);
        roleCredentials.put(role, new RoleCredentials(username, password));
        roleSessionCache.put(role, authService.getSessionForUser(username, password));
    }

    public void evictSessionForRole(UserRole role) {
        RoleCredentials creds = credentialsFor(role);
        roleSessionCache.remove(role);
        authService.invalidateSession(creds.username(), creds.password());
    }

    /**
     * Drop role cookies and AuthService session cache so the next call re-logins.
     * Role bindings from {@link #setSessionForRole} are kept.
     */
    public void clearSessionCache() {
        roleSessionCache.clear();
        authService.clearSessionCache();
        log.debug("🧹 Session cache cleared");
    }

    private Response executeWithSessionRetry(UserRole role, Function<Map<String, String>, Response> call) {
        Response response = call.apply(getSessionForRole(role));
        if (!SessionUnauthorizedRetry.shouldRelogin(role, response.statusCode())) {
            return response;
        }
        log.warn("⚠️ {} returned 401 — re-login and retry once", role);
        evictSessionForRole(role);
        return call.apply(getSessionForRole(role));
    }

    private RoleCredentials credentialsFor(UserRole role) {
        return roleCredentials.getOrDefault(role, new RoleCredentials(role.getUsername(), role.getPassword()));
    }

    @Step("API Request: POST /relocations/receive as {role}")
    public Response executeRelocationReceive(RelocationInputRequest request, UserRole role) {
        return executeWithSessionRetry(role, cookies -> apiClient.executeMultipartPost(
                ApiEndpointDefinition.RELOCATION_POST_RECEIVE.getPath(),
                cookies,
                "request",
                request
        ));
    }

    @Step("API Request: PUT /relocations/{id}/receive as {role}")
    public Response executeRelocationUpdateReceive(Long relocationId,
                                                   Long storageId,
                                                   RelocationInputEditRequest request,
                                                   UserRole role) {
        String path = ApiEndpointDefinition.RELOCATION_PUT_UPDATE_RECEIVE.getPath(
                relocationId, storageId);
        return executeWithSessionRetry(role,
                cookies -> apiClient.executeMultipartPut(path, cookies, "request", request));
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
        String path = ApiEndpointDefinition.EQUIPMENT_RELOCATION_PUT_UPDATE_RECEIVE.getPath(
                relocationId, storageId);
        return executeWithSessionRetry(role,
                cookies -> apiClient.executeMultipartPut(path, cookies, "request", request));
    }

    @Step("API Request: POST /equipment as {role}")
    public Response executeEquipmentCreate(EquipmentCreateRequest request, UserRole role) {
        return executeWithSessionRetry(role, cookies -> apiClient.executeMultipartPost(
                ApiEndpointDefinition.EQUIPMENT_POST_CREATE.getPath(),
                cookies,
                "request",
                request));
    }

    /**
     * {@code POST /api/v1/defects} — multipart with JSON part {@code request}
     * (backend also accepts an optional {@code attachment} part, not used by erp-auto-test).
     */
    @Step("API Request: POST /defects as {role}")
    public Response executeDefectCreate(DefectRequest request, UserRole role) {
        return executeWithSessionRetry(role, cookies -> apiClient.executeMultipartPost(
                ApiEndpointDefinition.DEFECT_POST_CREATE.getPath(),
                cookies,
                "request",
                request));
    }

    @Step("API Request: PUT /defects/{defectId} as {role}")
    public Response executeDefectUpdate(Long defectId, DefectRequest request, UserRole role) {
        String path = ApiEndpointDefinition.DEFECT_PUT_UPDATE.getPath(defectId);
        return executeWithSessionRetry(role,
                cookies -> apiClient.executeMultipartPut(path, cookies, "request", request));
    }

    @Step("API Request: POST /incidents/relocations as {role}")
    public Response executeIncidentCreate(RelocationIncidentRequest request, UserRole role) {
        return executeWithSessionRetry(role, cookies -> apiClient.executeMultipartPost(
                ApiEndpointDefinition.INCIDENT_POST_CREATE.getPath(),
                cookies,
                "request",
                request));
    }

    /**
     * {@code POST /api/v1/project-production} — multipart with JSON part {@code request}
     * (backend also accepts optional {@code files} parts, not used by erp-auto-test).
     */
    @Step("API Request: POST /project-production as {role}")
    public Response executeProjectProductionCreate(ProjectProductionRequest request, UserRole role) {
        return executeWithSessionRetry(role, cookies -> apiClient.executeMultipartPost(
                ApiEndpointDefinition.PROJECT_PRODUCTION_POST_CREATE.getPath(),
                cookies,
                "request",
                request));
    }
}
