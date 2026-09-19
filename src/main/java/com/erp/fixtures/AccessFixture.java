package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.request.AccessGrantRequest;
import com.erp.models.request.AccessRevokeRequest;
import com.erp.models.response.AccessGrantResponse;
import com.erp.models.response.AccessPermissionResponse;
import com.erp.models.response.AccessRoleResponse;
import com.erp.models.response.EffectivePermissionResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.ApiResponseHelper;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fixture for the DB-backed access API. User profile setup belongs to {@link UserFixture};
 * roles, grants, scopes, and effective permissions belong here.
 */
public class AccessFixture extends BaseFixture {

    public AccessFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("Get canonical permission catalog")
    public List<AccessPermissionResponse> listCatalog() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.ACCESS_GET_CATALOG, UserRole.ADMIN);
        return ApiResponseHelper.parseList(response, AccessPermissionResponse.class, "GET access catalog");
    }

    @Step("Get access roles")
    public List<AccessRoleResponse> listRoles() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.ACCESS_GET_ROLES, UserRole.ADMIN);
        return ApiResponseHelper.parseList(response, AccessRoleResponse.class, "GET access roles");
    }

    @Step("Resolve access role by name: {roleName}")
    public AccessRoleResponse roleByName(String roleName) {
        String expected = normalize(roleName);
        return listRoles().stream()
                .filter(role -> normalize(role.getName()).equals(expected))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Access role not found: " + roleName));
    }

    @Step("Get access grants for user {userId}")
    public List<AccessGrantResponse> grants(String userId, boolean includeRevoked) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ACCESS_GET_USER_GRANTS,
                UserRole.ADMIN,
                Map.of("includeRevoked", includeRevoked),
                userId);
        return ApiResponseHelper.parseList(response, AccessGrantResponse.class, "GET user access grants");
    }

    @Step("Get effective permissions for user {userId} at storage {storageId}")
    public List<EffectivePermissionResponse> effective(String userId, Long storageId) {
        Map<String, Object> query = storageId == null ? Map.of() : Map.of("storageId", storageId);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ACCESS_GET_USER_EFFECTIVE,
                UserRole.ADMIN,
                query,
                userId);
        return ApiResponseHelper.parseList(response, EffectivePermissionResponse.class,
                "GET user effective permissions");
    }

    /**
     * Adds only missing grants for this exact scope. Global roles and permissions are normalized
     * by the backend to {@link GrantScopeKind#ALL}.
     */
    @Step("Ensure access grants for user {userId}")
    public List<AccessGrantResponse> ensureGrants(String userId,
                                                  List<String> roleNames,
                                                  List<String> permissionKeys,
                                                  GrantScopeKind scopeKind,
                                                  Long storageId) {
        List<String> requestedRoles = distinctNonBlank(roleNames);
        List<String> requestedKeys = distinctNonBlank(permissionKeys);
        if (requestedRoles.isEmpty() && requestedKeys.isEmpty()) {
            return List.of();
        }

        Map<String, AccessRoleResponse> rolesByName = listRoles().stream()
                .collect(Collectors.toMap(role -> normalize(role.getName()), Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
        List<AccessRoleResponse> resolvedRoles = requestedRoles.stream()
                .map(name -> {
                    AccessRoleResponse role = rolesByName.get(normalize(name));
                    if (role == null) {
                        throw new IllegalStateException("Access role not found: " + name);
                    }
                    return role;
                })
                .toList();

        Set<String> catalogKeys = listCatalog().stream()
                .map(AccessPermissionResponse::getKey)
                .collect(Collectors.toSet());
        List<String> unknownKeys = requestedKeys.stream().filter(key -> !catalogKeys.contains(key)).toList();
        if (!unknownKeys.isEmpty()) {
            throw new IllegalArgumentException("Unknown canonical permission keys: " + unknownKeys);
        }

        List<AccessGrantResponse> active = grants(userId, false);
        List<Long> missingRoleIds = resolvedRoles.stream()
                .filter(role -> active.stream().noneMatch(grant -> sameRoleGrant(grant, role, scopeKind, storageId)))
                .map(AccessRoleResponse::getId)
                .toList();
        List<String> missingKeys = requestedKeys.stream()
                .filter(key -> active.stream().noneMatch(grant -> samePermissionGrant(grant, key, scopeKind, storageId)))
                .toList();
        if (missingRoleIds.isEmpty() && missingKeys.isEmpty()) {
            return List.of();
        }

        AccessGrantRequest request = AccessGrantRequest.builder()
                .roleIds(missingRoleIds)
                .permissionKeys(missingKeys)
                .scopeKind(scopeKind)
                .storageId(scopeKind == GrantScopeKind.ALL ? null : storageId)
                .comment("Automated test fixture")
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ACCESS_POST_USER_GRANTS, UserRole.ADMIN, request, userId);
        return ApiResponseHelper.parseList(response, AccessGrantResponse.class, "POST user access grants");
    }

    @Step("Revoke active access grants for user {userId}")
    public void revokeAll(String userId) {
        for (AccessGrantResponse grant : grants(userId, false)) {
            revoke(grant.getId(), "Automated test fixture reset");
        }
    }

    @Step("Revoke access grant {grantId}")
    public AccessGrantResponse revoke(Long grantId, String comment) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ACCESS_POST_REVOKE_GRANT,
                UserRole.ADMIN,
                AccessRevokeRequest.builder().comment(comment).build(),
                grantId);
        validateSuccess(response, "Revoke access grant " + grantId);
        return response.as(AccessGrantResponse.class);
    }

    @Step("Replace access grants for user {userId}")
    public List<AccessGrantResponse> replaceGrants(String userId,
                                                   List<String> roleNames,
                                                   List<String> permissionKeys,
                                                   GrantScopeKind scopeKind,
                                                   Long storageId) {
        revokeAll(userId);
        return ensureGrants(userId, roleNames, permissionKeys, scopeKind, storageId);
    }

    public boolean hasEffectivePermission(String userId, String permissionKey, Long storageId) {
        return effective(userId, storageId).stream()
                .anyMatch(permission -> permissionKey.equals(permission.getKey()) && permission.isGranted());
    }

    private static boolean sameRoleGrant(AccessGrantResponse grant,
                                         AccessRoleResponse role,
                                         GrantScopeKind requestedScope,
                                         Long requestedStorageId) {
        if (!grant.isActive() || grant.getRole() == null || !role.getId().equals(grant.getRole().getId())) {
            return false;
        }
        GrantScopeKind expectedScope = role.getScopeKind() == com.erp.models.access.AccessScopeKind.GLOBAL
                ? GrantScopeKind.ALL : requestedScope;
        return sameScope(grant, expectedScope, requestedStorageId);
    }

    private static boolean samePermissionGrant(AccessGrantResponse grant,
                                               String permissionKey,
                                               GrantScopeKind requestedScope,
                                               Long requestedStorageId) {
        if (!grant.isActive() || !permissionKey.equals(grant.getPermissionKey())) {
            return false;
        }
        // The endpoint normalizes global keys to ALL. Treat an existing ALL grant as satisfying
        // any requested scope; LOCATION permissions still have to match exactly.
        if (grant.getScopeKind() == GrantScopeKind.ALL) {
            return true;
        }
        return sameScope(grant, requestedScope, requestedStorageId);
    }

    private static boolean sameScope(AccessGrantResponse grant,
                                     GrantScopeKind expectedScope,
                                     Long expectedStorageId) {
        if (grant.getScopeKind() != expectedScope) {
            return false;
        }
        Long actualStorageId = grant.getStorage() == null ? null : grant.getStorage().getId();
        Long normalizedExpected = expectedScope == GrantScopeKind.ALL ? null : expectedStorageId;
        return java.util.Objects.equals(actualStorageId, normalizedExpected);
    }

    private static List<String> distinctNonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
