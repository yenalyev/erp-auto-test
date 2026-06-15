package com.erp.data.factories.non_series_production;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;

import static com.erp.data.RequestBodyFactory.register;

public class NonSeriesProductionRequestBodyFactory {

    public static void registerStrategies() {
        register(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, context -> {
            Long resourceId = context.get(ContextKey.NON_SERIES_RESOURCE_ID);
            if (resourceId == null) {
                throw new IllegalStateException("NON_SERIES_RESOURCE_ID required for request body generation");
            }
            return NonSeriesProductionDataFactory.buildInProgressRequest(
                    ConfigProvider.getOwner1StorageId(), resourceId, 1.0, 1.0);
        });
    }
}
