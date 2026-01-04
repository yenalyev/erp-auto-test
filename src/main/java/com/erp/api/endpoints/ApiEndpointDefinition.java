package com.erp.api.endpoints;

import com.erp.api.types.TypeReference;
import com.erp.models.request.ResourceRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import io.restassured.http.Method;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 🎯 Central registry for ALL API endpoints
 * <p>
 * Features:
 * - Dynamic path variables support
 * - Request/Response class mapping with full generic support
 * - Schema validation
 * - Operation discriminator for duplicate paths
 * <p>
 * Uses TypeReference for type-safe generic handling
 */
@Getter
public enum ApiEndpointDefinition {

    // ========================================
    // RESOURCE ENDPOINTS
    // ========================================

    RESOURCE_GET_ALL(
            "/api/v1/resources",
            Method.GET,
            "schemas/resource-list-schema.json",
            "Get all resources - ",
            null,  // no request body
            new TypeReference<List<ResourceResponse>>() {},  // ✅ Type-safe List
            null
    ),

    RESOURCE_CREATE(
            "/api/v1/resources",
            Method.POST,
            "schemas/resource-response-schema.json",
            "Create new resource",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "CREATE"
    ),

    RESOURCE_UPDATE_NAME(
            "/api/v1/resources/{id}",
            Method.PUT,
            "schemas/resource-response-schema.json",
            "Update resource name",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "UPDATE_NAME"
    ),

    RESOURCE_UPDATE_UNIT(
            "/api/v1/resources/{id}",
            Method.PUT,
            "schemas/resource-response-schema.json",
            "Update resource measurement unit",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "UPDATE_UNIT"
    ),

    // ========================================
    // MEASUREMENT UNIT ENDPOINTS
    // ========================================
    MEASUREMENT_UNIT_GET_ALL(
            "/api/v1/measurement-unit",
            Method.GET,
            "schemas/measurement-unit-list-schema.json",
            "Get all measurement units",
            null,
            new TypeReference<List<MeasurementUnitResponse>>() {},
            null
    ),

    // ========================================
    // TECHNOLOGICAL MAP ENDPOINTS
    // ========================================

    TECH_MAP_GET_ALL(
            "/api/v1/technological-maps",
            Method.GET,
            "schemas/technological-maps/technological-map-list-schema.json",
            "Get all technological maps",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},  // ✅ Type-safe List
            null
    ),

    TECH_MAP_CREATE(
            "/api/v1/technological-maps",
            Method.POST,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Create technological map",
            new TypeReference<TechnologicalMapRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "CREATE"
    ),

    TECH_MAP_UPDATE(
            "/api/v1/technological-maps/{id}",
            Method.PUT,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Update technological map",
            new TypeReference<TechnologicalMapRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "UPDATE"
    );

    // ========================================
    // Fields
    // ========================================

    private final String pathTemplate;
    private final Method httpMethod;
    private final String schemaPath;
    private final String description;
    private final TypeReference<?> requestType;   // ✅ Full generic support
    private final TypeReference<?> responseType;  // ✅ Full generic support
    private final String operation;

    ApiEndpointDefinition(String pathTemplate, Method httpMethod, String schemaPath,
                          String description, TypeReference<?> requestType,
                          TypeReference<?> responseType, String operation) {
        this.pathTemplate = pathTemplate;
        this.httpMethod = httpMethod;
        this.schemaPath = schemaPath;
        this.description = description;
        this.requestType = requestType;
        this.responseType = responseType;
        this.operation = operation;
    }

    // ============================================
    // 🔧 Path Building Methods
    // ============================================

    public String getPathTemplate() {
        return pathTemplate;
    }

    /**
     * ✅ Build path with dynamic parameters
     */
    public String getPath(Object... params) {
        if (params == null || params.length == 0) {
            return pathTemplate;
        }

        String result = pathTemplate;
        int paramIndex = 0;

        while (result.contains("{") && paramIndex < params.length) {
            int start = result.indexOf("{");
            int end = result.indexOf("}", start);

            if (start != -1 && end != -1) {
                String placeholder = result.substring(start, end + 1);
                result = result.replace(placeholder, String.valueOf(params[paramIndex]));
                paramIndex++;
            } else {
                break;
            }
        }

        return result;
    }

    public boolean hasPathVariables() {
        return pathTemplate.contains("{");
    }

    public int getPathVariablesCount() {
        int count = 0;
        String temp = pathTemplate;
        while (temp.contains("{")) {
            count++;
            temp = temp.substring(temp.indexOf("}") + 1);
        }
        return count;
    }

    // ============================================
    // 🎯 Type-related Methods
    // ============================================

    /**
     * Get request class (raw type)
     */
    public Class<?> getRequestClass() {
        return requestType != null ? requestType.getRawType() : null;
    }

    /**
     * Get response class (raw type)
     */
    public Class<?> getResponseClass() {
        return responseType != null ? responseType.getRawType() : null;
    }

    /**
     * Get full request Type (with generics)
     */
    public Type getRequestFullType() {
        return requestType != null ? requestType.getType() : null;
    }

    /**
     * Get full response Type (with generics)
     */
    public Type getResponseFullType() {
        return responseType != null ? responseType.getType() : null;
    }

    /**
     * ✅ Check if response is a collection
     */
    public boolean isCollectionResponse() {
        return responseType != null && responseType.isCollection();
    }

    /**
     * ✅ Get element type for collection responses
     * <p>
     * For List<ResourceResponse> returns ResourceResponse.class
     */
    public Class<?> getResponseElementType() {
        if (responseType != null) {
            return responseType.getElementType();
        }
        return null;
    }

    /**
     * Get response type description (human-readable)
     */
    public String getResponseTypeDescription() {
        return responseType != null ? responseType.getTypeDescription() : "void";
    }

    /**
     * Get request type description (human-readable)
     */
    public String getRequestTypeDescription() {
        return requestType != null ? requestType.getTypeDescription() : "none";
    }

    // ============================================
    // 🔍 Search Methods
    // ============================================

    public static ApiEndpointDefinition findByName(String name) {
        try {
            return ApiEndpointDefinition.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown endpoint: " + name + ". Available: " +
                            Arrays.toString(ApiEndpointDefinition.values())
            );
        }
    }

    public static Optional<ApiEndpointDefinition> findByPathAndMethod(String pathTemplate, Method method) {
        long count = Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate) && e.getHttpMethod() == method)
                .count();

        if (count > 1) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate) && e.getHttpMethod() == method)
                .findFirst();
    }

    public static ApiEndpointDefinition findByPathMethodAndOperation(
            String pathTemplate, Method method, String operation) {
        return Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate)
                        && e.getHttpMethod() == method
                        && operation.equals(e.getOperation()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No endpoint found for %s %s [%s]", method, pathTemplate, operation)
                ));
    }

    /**
     * ✅ Find endpoint by request class
     */
    public static Optional<ApiEndpointDefinition> findByRequestClass(Class<?> requestClass) {
        return Arrays.stream(values())
                .filter(e -> e.requestType != null &&
                        requestClass.equals(e.requestType.getRawType()))
                .findFirst();
    }

    // ============================================
    // 🎯 Utility Methods
    // ============================================

    public boolean requiresBody() {
        return requestType != null &&
                requestType.getRawType() != null &&
                requestType.getRawType() != Void.class;
    }

    public boolean hasSchema() {
        return schemaPath != null;
    }

    public String getUniqueKey() {
        String key = httpMethod.name() + ":" + pathTemplate;
        if (operation != null) {
            key += ":" + operation;
        }
        return key;
    }

    @Override
    public String toString() {
        return String.format("%s %s [%s] → %s",
                httpMethod,
                pathTemplate,
                operation != null ? operation : "default",
                getResponseTypeDescription()
        );
    }
}