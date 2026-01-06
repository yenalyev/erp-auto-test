package com.erp.data.factories.tech_map;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;

import java.util.List;
import java.util.UUID;

import static com.erp.data.RequestBodyFactory.register;
import static com.erp.data.factories.tech_map.TechnologicalMapDataFactory.createSimpleTechMap;

public class TechnologicalMapRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/technological-maps
        register(ApiEndpointDefinition.TECH_MAP_CREATE, context -> {
            List<ResourceResponse> sharedResourceList = context.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
                    if (sharedResourceList == null || sharedResourceList.isEmpty()
                            || sharedResourceList.size()<4) {
                        throw new IllegalStateException("ERROR - Test Setup Error: 'sharedResourceList' is " +
                                "missing or too small in RbacTestContext. " +
                                "Make sure RbacTestFixtures.prepareEnvironment() was called.");
                    }
                    return createSimpleTechMap(sharedResourceList).build();
                }

        );


        // PUT /api/v1/technological-maps/{id}
        // Використовується для тестування оновлення імені
        register(ApiEndpointDefinition.TECH_MAP_UPDATE_NAME, context -> {
            // Get dynamic Tech map from  ErpFixture.prepareTechMapForUpdate()
            TechnologicalMapResponse existingMap = context.get(ContextKey.DYNAMIC_TECH_MAP);

            if (existingMap == null) {
                throw new IllegalStateException("ERROR - Test Setup Error: 'DYNAMIC_TECH_MAP' " +
                        "is missing in context. " +
                        "Make sure erpFixture.prepareTechMapForUpdate() was called in the test setup.");
            }

            String newName = "Updated Name " + UUID.randomUUID();
            context.set(ContextKey.DYNAMIC_TECH_MAP_NEW_NAME, newName);
            return TechnologicalMapDataFactory.fromExisting(existingMap).name(newName).build();
        });
    }
}
