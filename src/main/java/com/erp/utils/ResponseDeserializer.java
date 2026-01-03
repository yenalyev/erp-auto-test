package com.erp.utils;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

import java.util.List;

/**
 * 🔧 Response deserializer with full generic support
 */
public class ResponseDeserializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ✅ Deserialize response based on endpoint definition
     * <p>
     * Automatically handles:
     * - Simple objects (ResourceResponse)
     * - Collections (List<ResourceResponse>)
     * - Complex generics (Map<String, List<Resource>>)
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Response response, ApiEndpointDefinition endpoint) {
        if (endpoint.isCollectionResponse()) {
            // For collections, use RestAssured's built-in support
            Class<?> elementType = endpoint.getResponseElementType();
            return (T) response.jsonPath().getList("", elementType);
        } else {
            // For single objects
            return (T) response.as(endpoint.getResponseClass());
        }
    }

    /**
     * ✅ Type-safe collection deserialization
     */
    public static <T> List<T> deserializeList(Response response, Class<T> elementClass) {
        return response.jsonPath().getList("", elementClass);
    }

    /**
     * ✅ Advanced deserialization using Jackson for complex types
     */
    public static <T> T deserializeAdvanced(Response response, ApiEndpointDefinition endpoint) {
        try {
            String json = response.asString();
            return objectMapper.readValue(json,
                    objectMapper.constructType(endpoint.getResponseFullType()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response for " + endpoint, e);
        }
    }
}