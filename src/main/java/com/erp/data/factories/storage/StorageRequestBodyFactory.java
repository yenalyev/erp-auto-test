package com.erp.data.factories.storage;


import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public class StorageRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/storages - create with random name
        register(ApiEndpointDefinition.STORAGE_POST_CREATE, context ->
                StorageDataFactory.randomStorage().build()
        );


        // PUT /api/v1/storages/{id} - update with new random name
        register(ApiEndpointDefinition.STORAGE_PUT_UPDATE, context -> {
            StorageResponse existingStorage = context.get(ContextKey.DYNAMIC_STORAGE);
            return StorageDataFactory.updateNameFromExisting(existingStorage,
                    FakerProvider.ukrainian().company().name()).build();
                }
        );

        //TODO Get Store for specific business owner
        // PUT /api/v1/storages/{id} - update with new random name
        register(ApiEndpointDefinition.STORAGE_PUT_UPDATE, context -> {
                    StorageResponse existingStorage = context.get(ContextKey.DYNAMIC_STORAGE);
                    return StorageDataFactory.updateNameFromExisting(existingStorage,
                            FakerProvider.ukrainian().company().name()).build();
                }
        );
    }
}
