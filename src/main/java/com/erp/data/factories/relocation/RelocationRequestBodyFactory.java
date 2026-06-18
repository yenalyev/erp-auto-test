package com.erp.data.factories.relocation;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.RelocationState;
import com.erp.models.request.EquipmentRelocationSendRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.erp.data.RequestBodyFactory.register;

public class RelocationRequestBodyFactory {
    public static void registerStrategies() {
        register(ApiEndpointDefinition.RELOCATION_POST_SEND, context -> {
            Long fromStoreId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            Long toStoreId = context.get(ContextKey.OWNER_2_STORAGE_ID);
            ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
            return RelocationDataFactory.simpleRelocation(fromStoreId,
                    toStoreId, resource, BigDecimal.valueOf(5)).build();
        });
        register(ApiEndpointDefinition.RELOCATION_POST_CREATE_BY_STORE_ID, context -> {
            Long fromStoreId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            Long toStoreId = context.get(ContextKey.OWNER_2_STORAGE_ID);
            ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
            return RelocationDataFactory.simpleRelocation(fromStoreId,
                    toStoreId, resource, BigDecimal.valueOf(5)).build();
        });
        register(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE, context ->
                RelocationUpdateRequest.builder()
                        .state(RelocationState.FINISHED)
                        .description("rbac resolve")
                        .build());
        register(ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND, context ->
                buildSendEdit(context, 2.0));
        register(ApiEndpointDefinition.RELOCATION_PUT_UPDATE_RECEIVE, context ->
                buildReceiveEdit(context, 4.0));
        register(ApiEndpointDefinition.EQUIPMENT_RELOCATION_POST_SEND, context -> {
            Long from = context.get(ContextKey.OWNER_1_STORAGE_ID);
            Long to = context.get(ContextKey.OWNER_2_STORAGE_ID);
            Long equipmentId = context.get(ContextKey.EQUIPMENT_ID);
            return EquipmentRelocationSendRequest.builder()
                    .fromStorageId(from)
                    .toStorageId(to)
                    .equipmentIds(List.of(equipmentId))
                    .date(LocalDate.now())
                    .description("rbac equipment send")
                    .build();
        });
    }

    private static RelocationOutputEditRequest buildSendEdit(TestContext context, double amount) {
        Long resourceId = context.get(ContextKey.RELOCATION_RESOURCE_ID);
        return RelocationDataFactory.buildSendEditRequest(resourceId, amount, "rbac edit send");
    }

    private static RelocationInputEditRequest buildReceiveEdit(TestContext context, double amount) {
        Long resourceId = context.get(ContextKey.RELOCATION_RESOURCE_ID);
        return RelocationDataFactory.buildReceiveEditRequest(
                resourceId, amount, "rbac-batch", "rbac edit receive");
    }
}
