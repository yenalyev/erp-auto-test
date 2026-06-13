package com.erp.data.factories.production;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import static com.erp.data.RequestBodyFactory.register;

public class ProductionRequestBodyFactory {
    public static void registerStrategies() {
        register(ApiEndpointDefinition.PRODUCTION_POST_CREATE, context -> {
            Long storeId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            TechnologicalMapResponse techMap = context.get(ContextKey.DYNAMIC_TECH_MAP);
            if (techMap == null) {
                techMap = context.get(ContextKey.PRODUCTION_TECH_MAP);
            }
            return ProductionDataFactory.buildCreateRequest(techMap, 1.0);
        });
    }
}
