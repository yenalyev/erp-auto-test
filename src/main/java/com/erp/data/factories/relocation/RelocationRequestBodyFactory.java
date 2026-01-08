package com.erp.data.factories.relocation;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public class RelocationRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/productions?storageId={id} - create simple production
        register(ApiEndpointDefinition.RELOCATION_POST_CREATE_BY_STORE_ID, context -> {
                    Long fromStoreId = context.get(ContextKey.OWNER_1_STORAGE_ID);
                    Long toStoreId = context.get(ContextKey.OWNER_2_STORAGE_ID);
            ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
            return RelocationDataFactory.simpleRelocation(fromStoreId,
                    toStoreId, resource, FakerProvider.price(1D, 100D)).build();
                }
        );
    }
}
