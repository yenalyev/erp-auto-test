package com.erp.data.factories.order;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;

import static com.erp.data.RequestBodyFactory.register;

public final class OrderRequestBodyFactory {

    private OrderRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.ORDER_POST_CREATE, OrderRequestBodyFactory::buildCreateOrUpdate);
        register(ApiEndpointDefinition.ORDER_PUT_UPDATE, OrderRequestBodyFactory::buildCreateOrUpdate);
        register(ApiEndpointDefinition.ORDER_PUT_GATHERING_STORAGE, context -> {
            Long gatheringStorageId = context.get(ContextKey.ORDER_GATHERING_STORAGE_ID);
            if (gatheringStorageId == null) {
                gatheringStorageId = context.get(ContextKey.OWNER_2_STORAGE_ID);
            }
            return OrderDataFactory.buildGatheringStorageRequest(gatheringStorageId);
        });
        register(ApiEndpointDefinition.ORDER_POST_BOOKING, context -> {
            Long resourceId = context.get(ContextKey.ORDER_RESOURCE_ID);
            if (resourceId == null) {
                resourceId = context.get(ContextKey.SHARED_RESOURCE_ID);
            }
            return OrderDataFactory.buildBookingRequest(resourceId, 5.0);
        });
        register(ApiEndpointDefinition.ORDER_PUT_BOOKING_PREPARED, context ->
                OrderDataFactory.buildPreparedRequest(true));
        register(ApiEndpointDefinition.ORDER_PUT_BOOKINGS_PREPARED, context ->
                OrderDataFactory.buildPreparedRequest(true));
        register(ApiEndpointDefinition.ORDER_POST_COMMENT, context ->
                OrderDataFactory.buildCommentRequest());
    }

    private static Object buildCreateOrUpdate(TestContext context) {
        Long storageId = context.get(ContextKey.ORDER_REQUESTER_STORAGE_ID);
        if (storageId == null) {
            storageId = context.get(ContextKey.OWNER_1_STORAGE_ID);
        }
        Long resourceId = context.get(ContextKey.ORDER_RESOURCE_ID);
        if (resourceId == null) {
            resourceId = context.get(ContextKey.SHARED_RESOURCE_ID);
        }
        return OrderDataFactory.buildOrderRequest(storageId, resourceId, 5.0);
    }
}
