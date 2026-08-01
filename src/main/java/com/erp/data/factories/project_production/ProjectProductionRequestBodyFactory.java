package com.erp.data.factories.project_production;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;

import static com.erp.data.RequestBodyFactory.register;

/**
 * RBAC / functional request-body strategies for the Project Production domain.
 * Bodies are scoped to OWNER_1's storage so that OWNER_2 is correctly denied (403),
 * mirroring {@code NonSeriesProductionRequestBodyFactory}.
 */
public class ProjectProductionRequestBodyFactory {

    public static void registerStrategies() {
        register(ApiEndpointDefinition.PROJECT_PRODUCTION_POST_CREATE, context -> {
            Long resourceId = context.get(ContextKey.PROJECT_RESOURCE_ID);
            if (resourceId == null) {
                throw new IllegalStateException("PROJECT_RESOURCE_ID required for project production create body generation");
            }
            Long categoryId = context.get(ContextKey.PROJECT_CATEGORY_ID);
            Long productId = context.get(ContextKey.PROJECT_PRODUCT_ID);
            return ProjectProductionDataFactory.buildCreateRequestWithStage(
                    ConfigProvider.getOwner1StorageId(), categoryId, productId, resourceId, 1.0, 0.0);
        });

        register(ApiEndpointDefinition.PROJECT_PRODUCTION_PUT_UPDATE, context -> {
            Long resourceId = context.get(ContextKey.PROJECT_RESOURCE_ID);
            if (resourceId == null) {
                throw new IllegalStateException("PROJECT_RESOURCE_ID required for project production update body generation");
            }
            Long categoryId = context.get(ContextKey.PROJECT_CATEGORY_ID);
            Long productId = context.get(ContextKey.PROJECT_PRODUCT_ID);
            return ProjectProductionDataFactory.buildCreateRequestWithStage(
                    ConfigProvider.getOwner1StorageId(), categoryId, productId, resourceId, 1.0, 0.0);
        });

        register(ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_POST_CREATE, context -> {
            Long categoryId = context.get(ContextKey.PROJECT_CATEGORY_ID);
            Long productId = context.get(ContextKey.PROJECT_PRODUCT_ID);
            return ProjectProductionDataFactory.buildTemplateCreateRequest(
                    ConfigProvider.getOwner1StorageId(), categoryId, productId, null);
        });

        register(ApiEndpointDefinition.PROJECT_CATEGORY_POST_CREATE,
                context -> ProjectProductionDataFactory.buildCategoryCreateRequest());

        register(ApiEndpointDefinition.PROJECT_PRODUCT_POST_CREATE, context -> {
            Long categoryId = context.get(ContextKey.PROJECT_CATEGORY_ID);
            if (categoryId == null) {
                throw new IllegalStateException("PROJECT_CATEGORY_ID required for project product create body generation");
            }
            return ProjectProductionDataFactory.buildProductCreateRequest(categoryId);
        });
    }
}
