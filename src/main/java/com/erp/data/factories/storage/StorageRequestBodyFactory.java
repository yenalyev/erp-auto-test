package com.erp.data.factories.storage;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;

import static com.erp.data.RequestBodyFactory.register;

public class StorageRequestBodyFactory {
    public static void registerStrategies() {
        register(ApiEndpointDefinition.STORAGE_POST_CREATE, context -> {
            Long parentId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            if (parentId == null) {
                parentId = ConfigProvider.getOwner1StorageId();
            }
            return StorageDataFactory.childStorage(parentId, "rbac-").build();
        });

        register(ApiEndpointDefinition.STORAGE_PUT_UPDATE, context -> {
            StorageResponse existingStorage = context.get(ContextKey.DYNAMIC_STORAGE);
            return StorageDataFactory.fromExisting(existingStorage)
                    .name(FakerProvider.ukrainian().company().name())
                    .build();
        });
    }
}
