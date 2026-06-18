package com.erp.data.factories.defect;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;

import static com.erp.data.RequestBodyFactory.register;

/**
 * RBAC request-body strategies for defect endpoints.
 * Bodies are scoped to OWNER_1's storage so that OWNER_2 is correctly denied (403).
 */
public class DefectRequestBodyFactory {

    public static void registerStrategies() {
        register(ApiEndpointDefinition.DEFECT_POST_CREATE, context -> {
            Long resourceId = context.get(ContextKey.DEFECT_RESOURCE_ID);
            if (resourceId == null) {
                throw new IllegalStateException("DEFECT_RESOURCE_ID required for defect create body generation");
            }
            return DefectDataFactory.buildStorageFifoDefect(
                    ConfigProvider.getOwner1StorageId(), resourceId, 1.0);
        });

        register(ApiEndpointDefinition.DEFECT_POST_WRITE_OFF, context -> {
            Long defectId = context.get(ContextKey.DEFECT_ID);
            if (defectId == null) {
                throw new IllegalStateException("DEFECT_ID required for defect write-off body generation");
            }
            return DefectDataFactory.buildWriteOff(
                    defectId, ConfigProvider.getOwner1StorageId(), 1.0, "erp-auto-test rbac write-off");
        });
    }
}
