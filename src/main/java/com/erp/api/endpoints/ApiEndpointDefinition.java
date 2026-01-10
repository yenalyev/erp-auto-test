package com.erp.api.endpoints;

import com.erp.api.types.TypeReference;
import com.erp.models.request.*;
import com.erp.models.response.*;
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

    MEASUREMENT_UNIT_POST_CREATE(
            "/api/v1/measurement-unit",
            Method.POST,
            "schemas/measurement-unit-schema.json",
            "Create measurement unit",
            new TypeReference<MeasurementUnitResponse>() {},
            new TypeReference<MeasurementUnitResponse>() {},
            "CREATE_MEASUREMENT_UNIT"
    ),

    MEASUREMENT_UNIT_POST_CREATE_INVALID_NAME(
            "/api/v1/measurement-unit",
            Method.POST,
            "schemas/measurement-unit-schema.json",
            "Create measurement unit",
            new TypeReference<MeasurementUnitResponse>() {},
            new TypeReference<MeasurementUnitResponse>() {},
            "CREATE_MEASUREMENT_UNIT"
    ),


    // ========================================
    // TECHNOLOGICAL MAP ENDPOINTS
    // ========================================

    TECH_MAP_GET_ALL(
            "/api/v1/technological-maps",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
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
            "CREATE_TECH_MAP"
    ),

    TECH_MAP_UPDATE_NAME(
            "/api/v1/technological-maps/{id}",
            Method.PUT,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Update name in technological map",
            new TypeReference<TechnologicalMapRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "UPDATE_TECH_MAP_NAME"
    ),

    // ========================================
    // TECHNOLOGICAL MAP ENDPOINTS
    // ========================================

    STORAGE_GET_ALL(
            "/api/v1/storages",
            Method.GET,
            "schemas/storages/storage-response-list-schema.json",
            "Get all storages",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_POST_CREATE(
            "/api/v1/storages",
            Method.POST,
            "schemas/storages/storage-response-schema.json",
            "Create new storage",
            new TypeReference<StorageRequest>() {},
            new TypeReference<StorageResponse>() {},
            "CREATE_STORAGE"
    ),

    STORAGE_PUT_UPDATE(
            "/api/v1/storages/{id}",
            Method.PUT,
            "schemas/storages/storage-response-schema.json",
            "Update storage name",
            new TypeReference<StorageRequest>() {},
            new TypeReference<StorageResponse>() {},
            "UPDATE_STORAGE"
    ),

    // ========================================
    // STATISTIC ENDPOINTS
    // ========================================

    STATISTIC_GET_PLAN(
            "/api/v1/statistics/plan?storageId={id}",
            Method.GET,
            "schemas/statistics/plan-statistics-response-schema.json",
            "Get statistic plan",
            null,
            new TypeReference<PlanStatisticsResponse>() {},
            null
    ),

    // ========================================
    // PLAN ENDPOINTS
    // ========================================

    PLAN_GET_ALL(
            "/api/v1/plans?storageId={id}",
            Method.GET,
            "schemas/plans/plan-response-list-schema.json",
            "Get all plans",
            null,
            new TypeReference<List<PlanResponse>>() {},
            null
    ),

    PLAN_POST_CREATE(
            "/api/v1/plans",
            Method.POST,
            "schemas/plans/plan-response-schema.json",
            "Create plan",
            new TypeReference<PlanRequest>() {},
            new TypeReference<PlanResponse>() {},
            "CREATE_PLAN"
    ),

    PLAN_PUT_UPDATE(
            "/api/v1/plans/{id}",
            Method.PUT,
            "schemas/plans/plan-response-schema.json",
            "Update plan",
            new TypeReference<PlanRequest>() {},
            new TypeReference<PlanResponse>() {},
            "UPDATE_PLAN"
    ),

    // ========================================
    // PRODUCTION ENDPOINTS
    // ========================================

    PRODUCTION_GET_ALL_BY_STORE_ID(
            "/api/v1/productions?storageId={id}",
            Method.GET,
            "schemas/productions/production-response-list-schema.json",
            "Get all production by store",
            null,
            new TypeReference<List<ProductionResponse>>() {},
            null
    ),

    PRODUCTION_POST_CREATE_BY_OWNER_1_STORE_ID(
            "/api/v1/productions",
            Method.POST,
            "schemas/productions/production-response-schema.json",
            "Create production by store",
            new TypeReference<ProductionRequest>() {},
            new TypeReference<ProductionResponse>() {},
            "CREATE_PRODUCTIONS"
    ),

    // ========================================
    // RELOCATION ENDPOINTS
    // ========================================
    RELOCATION_GET_ALL_BY_STORE_ID(
            "/api/v1/relocations?storageId={id}",
            Method.GET,
            "schemas/relocations/relocation-response-list-schema.json",
            "Get all relocation by store",
            null,
            new TypeReference<List<RelocationResponse>>() {},
            null
    ),

    RELOCATION_POST_CREATE_BY_STORE_ID(
            "/api/v1/relocations",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
                    "Create relocation by store",
                    new TypeReference<RelocationRequest>() {},
                    new TypeReference<ResourceResponse>() {},
            "CREATE_RELOCATIONS"
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