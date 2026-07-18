package com.erp.data.factories.incident;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public final class IncidentRequestBodyFactory {

    private IncidentRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.INCIDENT_POST_CREATE, context -> {
            Long relocationId = context.get(ContextKey.RELOCATION_FOR_INCIDENT_ID);
            Long senderId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            Long resourceId = context.get(ContextKey.RELOCATION_RESOURCE_ID);
            if (relocationId == null) {
                relocationId = context.get(ContextKey.RELOCATION_CREATED_ID);
            }
            return IncidentDataFactory.buildWriteOff(
                    relocationId, senderId, resourceId, 1.0, "rbac incident create");
        });
    }
}
