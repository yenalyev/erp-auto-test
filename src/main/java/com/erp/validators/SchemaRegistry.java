package com.erp.validators;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.rbac.EndpointAccessRule;
import io.restassured.response.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * 📋 Schema Registry - JSON Schema validation utility
 * <p>
 * Acts as a facade over ApiEndpointDefinition for schema-related operations.
 * Now uses ApiEndpointDefinition as single source of truth for schemas.
 * <p>
 * Features:
 * - Schema path retrieval from endpoint definitions
 * - Automatic response validation against schemas
 * - Null-safe operations (handles endpoints without schemas like DELETE)
 * - Integration with EndpointAccessRule for RBAC tests
 * <p>
 * Usage:
 * <pre>
 * // Get schema path
 * String schemaPath = SchemaRegistry.getSchemaPath(ApiEndpointDefinition.RESOURCE_GET_ALL);
 *
 * // Validate response
 * SchemaRegistry.validate(response, ApiEndpointDefinition.RESOURCE_GET_ALL);
 *
 * // Conditional validation
 * SchemaRegistry.validateIfSuccess(response, endpoint);
 * </pre>
 *
 * @deprecated Old HashMap-based approach removed. All schema info is now in ApiEndpointDefinition.
 */
@Slf4j
@UtilityClass
public class SchemaRegistry {

    // ============================================
    // 🎯 Schema Path Retrieval Methods
    // ============================================

    /**
     * Get schema path for endpoint definition
     *
     * @param endpoint Endpoint definition
     * @return Schema path or null if no schema exists
     */
    public static String getSchemaPath(ApiEndpointDefinition endpoint) {
        if (endpoint == null) {
            log.warn("⚠️ Cannot get schema path for null endpoint");
            return null;
        }
        return endpoint.getSchemaPath();
    }

    /**
     * Get schema path from EndpointAccessRule
     *
     * @param rule RBAC access rule containing endpoint info
     * @return Schema path or null if no schema exists
     */
    public static String getSchemaPath(EndpointAccessRule rule) {
        if (rule == null) {
            log.warn("⚠️ Cannot get schema path for null rule");
            return null;
        }

        try {
            ApiEndpointDefinition endpoint = rule.getEndpointDefinition();
            return getSchemaPath(endpoint);
        } catch (Exception e) {
            log.error("❌ Failed to get endpoint definition from rule: {}", rule.getEndpointName(), e);
            return null;
        }
    }

    // ============================================
    // ✅ Schema Existence Checks
    // ============================================

    /**
     * Check if endpoint has a schema
     *
     * @param endpoint Endpoint definition
     * @return true if schema exists, false otherwise
     */
    public static boolean hasSchema(ApiEndpointDefinition endpoint) {
        if (endpoint == null) {
            return false;
        }
        return endpoint.hasSchema();
    }

    /**
     * Check if endpoint access rule has a schema
     *
     * @param rule RBAC access rule
     * @return true if schema exists, false otherwise
     */
    public static boolean hasSchema(EndpointAccessRule rule) {
        if (rule == null) {
            return false;
        }
        try {
            return hasSchema(rule.getEndpointDefinition());
        } catch (Exception e) {
            log.error("❌ Failed to check schema existence for rule: {}", rule.getEndpointName(), e);
            return false;
        }
    }

    // ============================================
    // 🔍 Automatic Response Validation
    // ============================================

    /**
     * ✅ Validate response against endpoint's JSON schema
     * <p>
     * Automatically validates if schema exists. Silently skips validation
     * for endpoints without schemas (e.g., DELETE operations).
     *
     * @param response Response to validate
     * @param endpoint Endpoint definition
     * @throws AssertionError if validation fails
     */
    public static void validate(Response response, ApiEndpointDefinition endpoint) {
        if (endpoint == null) {
            log.warn("⚠️ Cannot validate response for null endpoint");
            return;
        }

        if (!hasSchema(endpoint)) {
            log.debug("ℹ️ Skipping schema validation for {} - no schema defined", endpoint);
            return;
        }

        String schemaPath = endpoint.getSchemaPath();
        log.debug("✅ Validating response against schema: {}", schemaPath);

        try {
            response.then()
                    .assertThat()
                    .body(matchesJsonSchemaInClasspath(schemaPath));

            log.debug("✅ Schema validation passed for {}", endpoint);
        } catch (AssertionError e) {
            log.error("❌ Schema validation failed for {}: {}", endpoint, e.getMessage());
            throw e;
        }
    }

    /**
     * ✅ Validate response with status code check
     * <p>
     * First checks status code, then validates schema if present
     *
     * @param response Response to validate
     * @param endpoint Endpoint definition
     * @param expectedStatus Expected HTTP status code
     */
    public static void validate(Response response, ApiEndpointDefinition endpoint, int expectedStatus) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }

        // First check status code
        response.then().statusCode(expectedStatus);
        log.debug("✅ Status code check passed: {}", expectedStatus);

        // Then validate schema
        validate(response, endpoint);
    }

    /**
     * ✅ Validate response from EndpointAccessRule
     * <p>
     * Convenience method for RBAC tests
     *
     * @param response Response to validate
     * @param rule RBAC access rule
     */
    public static void validate(Response response, EndpointAccessRule rule) {
        if (rule == null) {
            log.warn("⚠️ Cannot validate response for null rule");
            return;
        }

        try {
            ApiEndpointDefinition endpoint = rule.getEndpointDefinition();
            validate(response, endpoint);
        } catch (Exception e) {
            log.error("❌ Failed to validate response for rule: {}", rule.getEndpointName(), e);
            throw e;
        }
    }

    /**
     * ✅ Conditional validation - only validates if response is successful (2xx)
     * <p>
     * Useful when you want to validate schema only for successful responses,
     * while error responses might have different structure
     *
     * @param response Response to validate
     * @param endpoint Endpoint definition
     */
    public static void validateIfSuccess(Response response, ApiEndpointDefinition endpoint) {
        if (response == null) {
            log.warn("⚠️ Cannot validate null response");
            return;
        }

        int statusCode = response.getStatusCode();

        if (statusCode >= 200 && statusCode < 300) {
            log.debug("✅ Response is successful ({}), validating schema", statusCode);
            validate(response, endpoint);
        } else {
            log.debug("ℹ️ Skipping schema validation - response status: {}", statusCode);
        }
    }

    /**
     * ✅ Conditional validation from EndpointAccessRule
     *
     * @param response Response to validate
     * @param rule RBAC access rule
     */
    public static void validateIfSuccess(Response response, EndpointAccessRule rule) {
        if (rule == null) {
            log.warn("⚠️ Cannot validate response for null rule");
            return;
        }

        try {
            ApiEndpointDefinition endpoint = rule.getEndpointDefinition();
            validateIfSuccess(response, endpoint);
        } catch (Exception e) {
            log.error("❌ Failed to validate response for rule: {}", rule.getEndpointName(), e);
            throw e;
        }
    }

    // ============================================
    // 📊 Utility Methods
    // ============================================

    /**
     * Get human-readable description of validation status
     *
     * @param endpoint Endpoint definition
     * @return Description string
     */
    public static String getValidationDescription(ApiEndpointDefinition endpoint) {
        if (endpoint == null) {
            return "No endpoint specified";
        }

        if (hasSchema(endpoint)) {
            return String.format("Schema validation enabled: %s", endpoint.getSchemaPath());
        } else {
            return String.format("No schema validation for %s %s",
                    endpoint.getHttpMethod(), endpoint.getPathTemplate());
        }
    }

    /**
     * Log schema validation info
     *
     * @param endpoint Endpoint definition
     */
    public static void logSchemaInfo(ApiEndpointDefinition endpoint) {
        if (endpoint == null) {
            log.warn("⚠️ No endpoint specified");
            return;
        }

        log.info("📋 Schema info for {}:", endpoint);
        log.info("   - Has schema: {}", hasSchema(endpoint));
        if (hasSchema(endpoint)) {
            log.info("   - Schema path: {}", endpoint.getSchemaPath());
            log.info("   - Response type: {}", endpoint.getResponseTypeDescription());
        }
    }

    /**
     * Get count of endpoints with schemas
     *
     * @return Number of endpoints that have schemas defined
     */
    public static long getEndpointsWithSchemasCount() {
        return java.util.Arrays.stream(ApiEndpointDefinition.values())
                .filter(ApiEndpointDefinition::hasSchema)
                .count();
    }

    /**
     * Get count of total endpoints
     *
     * @return Total number of endpoints
     */
    public static long getTotalEndpointsCount() {
        return ApiEndpointDefinition.values().length;
    }

    /**
     * Get schema coverage percentage
     *
     * @return Percentage of endpoints with schemas (0-100)
     */
    public static double getSchemaCoveragePercentage() {
        long total = getTotalEndpointsCount();
        if (total == 0) {
            return 0.0;
        }
        long withSchemas = getEndpointsWithSchemasCount();
        return (withSchemas * 100.0) / total;
    }

    /**
     * Log schema coverage statistics
     */
    public static void logSchemaCoverage() {
        long total = getTotalEndpointsCount();
        long withSchemas = getEndpointsWithSchemasCount();
        double coverage = getSchemaCoveragePercentage();

        log.info("📊 Schema Coverage Statistics:");
        log.info("   - Total endpoints: {}", total);
        log.info("   - Endpoints with schemas: {}", withSchemas);
        log.info("   - Coverage: {:.1f}%", coverage);
    }
}