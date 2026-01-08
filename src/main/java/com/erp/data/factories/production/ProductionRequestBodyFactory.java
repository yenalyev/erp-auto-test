package com.erp.data.factories.production;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public class ProductionRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/productions?storageId={id} - create simple production
        register(ApiEndpointDefinition.PRODUCTION_POST_CREATE_BY_OWNER_1_STORE_ID, context -> {
            Long storeId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            TechnologicalMapResponse techMap = context.get(ContextKey.DYNAMIC_TECH_MAP);

            return
                    ProductionDataFactory.simpleProduction(storeId, techMap, 1D)
                            .build();
                }
        );
    }
}
