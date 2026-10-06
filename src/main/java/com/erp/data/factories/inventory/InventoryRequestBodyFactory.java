package com.erp.data.factories.inventory;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.request.InventoryProcessRequest;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.InventorySessionStatus;
import com.erp.utils.config.ConfigProvider;

import java.util.List;

import static com.erp.data.RequestBodyFactory.register;

public final class InventoryRequestBodyFactory {

    private InventoryRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.STORAGE_INVENTORY_REQUEST_POST, context ->
                InventoryProcessRequest.builder()
                        .storageId(ConfigProvider.getOwner1StorageId())
                        .comment("automation inventory request")
                        .build());
        register(ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_PUT, context ->
                InventoryProcessRequest.builder()
                        .storageId(ConfigProvider.getOwner1StorageId())
                        .comment("automation inventory approval")
                        .build());
        register(ApiEndpointDefinition.STORAGE_INVENTORY_REJECT_DELETE, context ->
                InventoryProcessRequest.builder()
                        .storageId(ConfigProvider.getOwner1StorageId())
                        .comment("automation inventory rejection")
                        .build());
        register(ApiEndpointDefinition.STORAGE_EQUIPMENT_INVENTORY_STATUS_PUT, context ->
                InventorySessionStatus.builder().open(true).build());
        register(ApiEndpointDefinition.STORAGE_INVENTORY_PUT, context ->
                InventoryRequest.builder().resources(List.of()).build());
    }
}
