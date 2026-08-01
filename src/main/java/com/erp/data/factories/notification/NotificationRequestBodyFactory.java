package com.erp.data.factories.notification;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public final class NotificationRequestBodyFactory {

    private NotificationRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE, context ->
                NotificationDataFactory.newActiveRecipient());

        register(ApiEndpointDefinition.NOTIFICATION_RECIPIENT_UPDATE, context -> {
            Integer id = context.get(ContextKey.NOTIFICATION_RECIPIENT_ID);
            return NotificationDataFactory.recipient(
                    NotificationDataFactory.uniqueCaption(),
                    NotificationDataFactory.randomPhone(),
                    NotificationDataFactory.STATE_ACTIVE).toBuilder()
                    .id(id)
                    .build();
        });

        register(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_SAVE, context -> {
            Integer recipientId = context.get(ContextKey.NOTIFICATION_RECIPIENT_ID);
            Long storageId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            return NotificationDataFactory.subscription(
                    recipientId,
                    NotificationDataFactory.TEMPLATE_STOCK_RED,
                    storageId != null ? java.util.List.of(storageId) : java.util.List.of());
        });

        register(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_DELETE, context -> {
            Integer recipientId = context.get(ContextKey.NOTIFICATION_RECIPIENT_ID);
            return NotificationDataFactory.removeSubscription(
                    recipientId, NotificationDataFactory.TEMPLATE_STOCK_RED);
        });
    }
}
