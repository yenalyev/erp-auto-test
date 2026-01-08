package com.erp.data.factories.storage;


import com.erp.api.endpoints.ApiEndpointDefinition;


import static com.erp.data.RequestBodyFactory.register;

public class StorageRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/storages - create with random name
        register(ApiEndpointDefinition.STORAGE_POST_CREATE, context ->
                StorageDataFactory.defaultStorage().build()
        );
    }
}
