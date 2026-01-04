package com.erp.models.rbac;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import io.restassured.http.Method;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 🔐 RBAC Access Control Rule
 * <p>
 * Defines access permissions for API endpoints based on user roles.
 * Now uses ApiEndpointDefinition as source of truth for endpoint metadata.
 * <p>
 * Features:
 * - Role-based access control (allowed/denied roles)
 * - Integration with ApiEndpointDefinition for endpoint details
 * - Runtime execution context (path params, request body)
 * - Validation and skip logic for test execution
 * <p>
 * Configuration in YAML:
 * <pre>
 * - endpointName: "RESOURCE_CREATE"
 *   allowedRoles: [ ADMIN ]
 *   deniedRoles: [ OWNER_1, ANONYMOUS ]
 *   bodyType: "CREATE"
 * </pre>
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointAccessRule {

    // ============================================
    // 🎯 Endpoint Configuration (from YAML)
    // ============================================

    /**
     * Endpoint definition name from ApiEndpointDefinition enum
     * <p>
     * Example: "RESOURCE_GET_ALL", "TECH_MAP_CREATE"
     */
    private String endpointName;

    /**
     * Roles that should have access to this endpoint
     */
    private Set<UserRole> allowedRoles;

    /**
     * Roles that should be denied access to this endpoint
     */
    private Set<UserRole> deniedRoles;

    /**
     * Body type / operation discriminator
     * <p>
     * Maps to ApiEndpointDefinition.operation for endpoints with duplicate path/method.
     * Examples: "CREATE", "UPDATE_NAME", "UPDATE_UNIT"
     */
    private String bodyType;

    // ============================================
    // 🔄 Runtime Context (set during test execution)
    // ============================================

    /**
     * Resource ID for path parameter (if endpoint requires it)
     * <p>
     * Set during test setup from shared resource creation
     */
    private String pathParam;

    /**
     * Pre-generated request body for POST/PUT requests
     * <p>
     * Set during test setup based on bodyType
     */
    private Object requestBody;

    // ============================================
    // 📦 Cached Endpoint Definition (lazy-loaded)
    // ============================================

    /**
     * Cached endpoint definition (lazy initialization)
     */
    private transient ApiEndpointDefinition endpointDefinition;

    // ============================================
    // 🔍 Endpoint Definition Access
    // ============================================

    /**
     * Get endpoint definition (lazy initialization)
     * <p>
     * Loads ApiEndpointDefinition by name on first access
     *
     * @return Endpoint definition
     * @throws IllegalArgumentException if endpoint name is invalid
     */
    public ApiEndpointDefinition getEndpointDefinition() {
        if (endpointDefinition == null) {
            try {
                endpointDefinition = ApiEndpointDefinition.findByName(endpointName);
                log.debug("✅ Loaded endpoint definition: {}", endpointDefinition);
            } catch (IllegalArgumentException e) {
                log.error("❌ Failed to load endpoint definition for name: {}", endpointName, e);
                throw e;
            }
        }
        return endpointDefinition;
    }

    // ============================================
    // 🎯 Convenience Delegation Methods
    // ============================================

    /**
     * Get endpoint path template
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public String getEndpoint() {
        return getEndpointDefinition().getPathTemplate();
    }

    /**
     * Get HTTP method
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public Method getHttpMethod() {
        return getEndpointDefinition().getHttpMethod();
    }

    /**
     * Get endpoint description
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public String getDescription() {
        return getEndpointDefinition().getDescription();
    }

    /**
     * Check if endpoint requires path ID
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public boolean requiresId() {
        return getEndpointDefinition().hasPathVariables();
    }

    /**
     * Check if endpoint requires request body
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public boolean requiresBody() {
        return getEndpointDefinition().requiresBody();
    }

    /**
     * Get request class for body generation
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public Class<?> getRequestClass() {
        return getEndpointDefinition().getRequestClass();
    }

    /**
     * Get response class for validation
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public Class<?> getResponseClass() {
        return getEndpointDefinition().getResponseClass();
    }

    /**
     * Get schema path for validation
     * <p>
     * Delegates to ApiEndpointDefinition
     */
    public String getSchemaPath() {
        return getEndpointDefinition().getSchemaPath();
    }

    /**
     * Check if endpoint has JSON schema
     */
    public boolean hasSchema() {
        return getEndpointDefinition().hasSchema();
    }

    // ============================================
    // 🛠️ Path Building Methods
    // ============================================

    /**
     * Get full endpoint path with path parameter if available
     * <p>
     * Examples:
     * - No ID required: "/api/v1/resources"
     * - ID required + available: "/api/v1/resources/123"
     * - ID required + not available: "/api/v1/resources/{id}" (template)
     *
     * @return Full path with ID substituted if available
     */
    public String getFullPath() {
        if (!requiresId()) {
            return getEndpoint();
        }

        if (pathParam != null) {
            return getEndpointDefinition().getPath(pathParam);
        }

        // Fallback to template if ID not available
        return getEndpointDefinition().getPathTemplate();
    }

    /**
     * Get full path with custom path parameters
     *
     * @param params Path parameters
     * @return Resolved path
     */
    public String getFullPath(Object... params) {
        return getEndpointDefinition().getPath(params);
    }

    // ============================================
    // ✅ Execution Validation
    // ============================================

    /**
     * Check if this rule can be executed with current runtime context
     * <p>
     * Rule can execute if:
     * - Endpoint doesn't require ID, OR
     * - Endpoint requires ID AND pathParam is set
     *
     * @return true if rule can execute, false otherwise
     */
    public boolean canExecute() {
        if (requiresId() && pathParam == null) {
            log.warn("⚠️ Cannot execute rule for {} - requires ID but pathParam is null", endpointName);
            return false;
        }

        if (requiresBody() && requestBody == null) {
            log.warn("⚠️ Cannot execute rule for {} - requires body but requestBody is null", endpointName);
            return false;
        }

        return true;
    }

    /**
     * Get reason why rule cannot be executed (for test skip messages)
     *
     * @return Skip reason or null if rule can execute
     */
    public String getSkipReason() {
        if (requiresId() && pathParam == null) {
            return String.format(
                    "⏭️ Endpoint '%s %s' requires resource ID, but shared resource was not created " +
                            "(backend may have returned error during setup)",
                    getHttpMethod(), getEndpoint()
            );
        }

        if (requiresBody() && requestBody == null) {
            return String.format(
                    "⏭️ Endpoint '%s %s' requires request body, but body was not generated " +
                            "(check bodyType configuration: '%s')",
                    getHttpMethod(), getEndpoint(), bodyType
            );
        }

        return null;
    }

    // ============================================
    // Information Methods
    // ============================================

    /**
     * Get detailed info for Allure attachment
     *
     * @param role       User role being tested
     * @param accessType Expected access type ("ALLOWED" / "DENIED")
     * @return Formatted info string
     */
    public String getDetailedInfo(UserRole role, String accessType) {
        ApiEndpointDefinition endpoint = getEndpointDefinition();

        return String.format(
                "📋 RBAC Test Details\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "Endpoint:          %s %s\n" +
                        "Definition:        %s\n" +
                        "Description:       %s\n" +
                        "Operation:         %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "Requirements:\n" +
                        "  - Requires ID:   %s\n" +
                        "  - Requires Body: %s\n" +
                        "  - Has Schema:    %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "Runtime Context:\n" +
                        "  - Path Param:    %s\n" +
                        "  - Request Body:  %s\n" +
                        "  - Full Path:     %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "RBAC Rules:\n" +
                        "  - Role:          %s\n" +
                        "  - Expected:      %s\n" +
                        "  - Allowed Roles: %s\n" +
                        "  - Denied Roles:  %s\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "Execution:\n" +
                        "  - Can Execute:   %s\n" +
                        "  - Skip Reason:   %s",
                getHttpMethod(),
                getEndpoint(),
                endpointName,
                getDescription(),
                bodyType != null ? bodyType : "default",
                requiresId(),
                requiresBody(),
                hasSchema(),
                pathParam != null ? pathParam : "null",
                requestBody != null ? requestBody.getClass().getSimpleName() : "null",
                getFullPath(),
                role,
                accessType,
                allowedRoles,
                deniedRoles,
                canExecute(),
                getSkipReason() != null ? getSkipReason() : "none"
        );
    }

    /**
     * Get compact info for logging
     *
     * @return One-line summary
     */
    public String getCompactInfo() {
        return String.format("%s %s [%s] - allowed: %s, denied: %s",
                getHttpMethod(),
                getEndpoint(),
                bodyType != null ? bodyType : "default",
                allowedRoles,
                deniedRoles
        );
    }

    /**
     * Check if role is explicitly allowed
     *
     * @param role User role to check
     * @return true if role is in allowedRoles
     */
    public boolean isRoleAllowed(UserRole role) {
        return allowedRoles != null && allowedRoles.contains(role);
    }

    /**
     * Check if role is explicitly denied
     *
     * @param role User role to check
     * @return true if role is in deniedRoles
     */
    public boolean isRoleDenied(UserRole role) {
        return deniedRoles != null && deniedRoles.contains(role);
    }

    /**
     * Get expected HTTP status for role
     *
     * @param role User role
     * @return Expected status code (200/201 for allowed, 403 for denied)
     */
    public int getExpectedStatus(UserRole role) {
        if (isRoleAllowed(role)) {
            return getHttpMethod() == Method.POST ? 201 : 200;
        } else if (isRoleDenied(role)) {
            return 403;
        }
        // Default to denied
        return 403;
    }

    @Override
    public String toString() {
        return String.format("EndpointAccessRule[%s]", getCompactInfo());
    }
}