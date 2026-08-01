package com.erp.data.factories.faita;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.request.ResourceReconciliationRequest;
import com.erp.models.request.SaveImplicitResourcesRequest;
import com.erp.models.response.FaitaResourceResponse;
import com.erp.test_context.ContextKey;

import java.util.List;
import java.util.UUID;

import static com.erp.data.RequestBodyFactory.register;

public final class FaitaRequestBodyFactory {

    private FaitaRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.RESOURCE_RECONCILIATION_CREATE, context -> {
            Long resourceId = context.get(ContextKey.SHARED_RESOURCE_ID);
            if (resourceId == null) {
                throw new IllegalStateException(
                        "SHARED_RESOURCE_ID missing — call prepareFullRbacContext / setupSharedResource first");
            }
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            return ResourceReconciliationRequest.builder()
                    .source("FLIGHT")
                    .externalId("rbac-recon-" + suffix)
                    .externalName("RBAC recon " + suffix)
                    .resourceIds(List.of(resourceId))
                    .build();
        });

        register(ApiEndpointDefinition.FAITA_IMPLICIT_RESOURCES_PUT, context -> {
            String externalId = context.get(ContextKey.FAITA_EXTERNAL_ID);
            if (externalId == null || externalId.isBlank()) {
                throw new IllegalStateException(
                        "FAITA_EXTERNAL_ID missing — call prepareFaitaRbacContext first");
            }
            return SaveImplicitResourcesRequest.builder()
                    .externalId(externalId)
                    .externalName("RBAC FAITA product")
                    .implicitResources(List.of(
                            FaitaResourceResponse.builder()
                                    .resourceId("rbac-impl-" + externalId)
                                    .resourceName("RBAC implicit resource")
                                    .build()))
                    .build();
        });
    }
}
