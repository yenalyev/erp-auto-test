package com.erp.data.factories;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;

import java.util.List;
import java.util.Objects;

import static com.erp.data.RequestBodyFactory.register;

public class ResourceRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/resources
        register(ApiEndpointDefinition.RESOURCE_CREATE, context -> {
                    Long unitId = context.get(ContextKey.SHARED_UNIT_ID);
                    if (unitId == null) {
                        throw new IllegalStateException("ERROR - Test Setup Error: 'sharedUnitId' is " +
                                "missing in RbacTestContext. Make sure RbacTestFixtures.prepareEnvironment() was called.");
                    }
                    return ResourceDataFactory.defaultResource(unitId).build();
                }

        );

        // PUT /api/v1/resources/{id} - Update Name
        register(ApiEndpointDefinition.RESOURCE_UPDATE_NAME, context -> {
            ResourceResponse existing = context.get(ContextKey.SHARED_RESOURCE);

            if (existing == null) {
                throw new IllegalStateException("Test Context Error: 'sharedResourceResponse' is null. " +
                        "Fix RbacTestFixtures to save the full response object.");
            }

            return ResourceDataFactory.fromExisting(existing)
                    .name("Updated Resource Name via RBAC: " + existing.getName())
                    .build();
        });

        // PUT /api/v1/resources/{id} - Update Unit (Negative Case)
        // Пробуємо змінити одиницю виміру для ресурсу (нова ід повинна відрізнятися від старої)
        //
        register(ApiEndpointDefinition.RESOURCE_UPDATE_UNIT, context -> {
            ResourceResponse existing = context.get(ContextKey.SHARED_RESOURCE);
            if (existing == null) {
                throw new IllegalStateException("Test Context Error: 'sharedResourceResponse' is null. " +
                        "Fix RbacTestFixtures to save the full response object.");
            }

            List<MeasurementUnitResponse> units = context.get(ContextKey.SHARED_MEASUREMENT_UNIT_LIST);
            if (units==null || units.isEmpty()){
                throw new IllegalStateException("Test Context Error: 'sharedAvailableMeasurementUnits' is null. " +
                        "Fix RbacTestFixtures to save the full response object.");
            }

            Long alternativeUnitId = units.stream()
                    .map(MeasurementUnitResponse::getId)
                    .filter(id -> !Objects.equals(id, existing.getUnit().getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Test Context Error: " +
                            "'sharedAvailableMeasurementUnits' has not ID " +
                            "which is different from measurement unit id from 'sharedResourceResponse'. " +
                            "Fix RbacTestFixtures to save the full response object." + "\n" +
                            "sharedResourceResponse: " + existing + "\n" +
                            "sharedAvailableMeasurementUnits: " + units));

            return ResourceDataFactory.fromExisting(existing)
                    .measurementUnitId(alternativeUnitId)
                    .build();
        });
    }
}
