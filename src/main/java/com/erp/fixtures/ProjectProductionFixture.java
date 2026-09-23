package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionStageRequest;
import com.erp.models.request.ProjectProductionTemplateRequest;
import com.erp.models.request.ResourceToRollbackRequest;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.ProjectProductionEquipmentResponse;
import com.erp.models.response.ProjectProductionParameterResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ProjectProductionStageResponse;
import com.erp.models.response.ProjectProductionTemplateResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fixture for equipment-oriented project production and its templates. */
@Slf4j
public class ProjectProductionFixture extends BaseFixture {

    public static final double DEFAULT_SEEDED_STOCK = 50.0;

    public ProjectProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів проєктного виробництва")
    public void prepareContext() {
        if (testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID) != null) {
            return;
        }

        Long storageId = ConfigProvider.getOwner1StorageId();
        ResourceResponse resource = resolveResourceForStorage(storageId);
        ensureStockAtLeast(storageId, resource.getId(), DEFAULT_SEEDED_STOCK);

        EquipmentCategoryResponse category = getEquipmentCategories().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No equipment categories available for project production"));
        List<SimpleEntityResponse> models = getEquipmentModels();
        Long modelId = models.isEmpty() ? null : models.getFirst().getId();

        ProjectProductionRequest sharedRequest = ProjectProductionDataFactory.buildCreateRequest(
                storageId, category.getId(), modelId,
                ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null);
        if (modelId == null) {
            sharedRequest = sharedRequest.toBuilder()
                    .equipmentModelName(ProjectProductionDataFactory.uniqueModelName())
                    .build();
        }
        ProjectProductionResponse shared = createAs(UserRole.ADMIN, sharedRequest);

        testContext.set(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID, shared.getEquipmentCategory().getId());
        testContext.set(ContextKey.PROJECT_EQUIPMENT_CATEGORY_NAME, shared.getEquipmentCategory().getName());
        testContext.set(ContextKey.PROJECT_EQUIPMENT_MODEL_ID, shared.getEquipmentModel().getId());
        testContext.set(ContextKey.PROJECT_EQUIPMENT_MODEL_NAME, shared.getEquipmentModel().getName());
        testContext.set(ContextKey.PROJECT_RESOURCE_ID, resource.getId());
        testContext.set(ContextKey.PROJECT_RESOURCE_NAME, resource.getName());
        testContext.set(ContextKey.PROJECT_SEEDED_STOCK, getResourceStock(storageId, resource.getId()));
        testContext.set(ContextKey.PROJECT_PRODUCTION_ID, shared.getId());

        log.info("Project production fixture ready: storage={}, equipmentCategory={}, equipmentModel={}, resource={}",
                storageId, shared.getEquipmentCategory().getId(), shared.getEquipmentModel().getId(), resource.getId());
    }

    private ResourceResponse resolveResourceForStorage(Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET, UserRole.ADMIN, String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(response, StorageItemResponse.class);
        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                return item.getResource();
            }
        }

        fetchSharedUnit(1);
        fetchSharedResourceCategory();
        setupSharedResource();
        ResourceResponse created = testContext.get(ContextKey.SHARED_RESOURCE);
        if (created == null) {
            throw new IllegalStateException("Failed to create resource for project production fixture");
        }
        return created;
    }

    public double getResourceStock(Long storageId, Long resourceId) {
        return ProductionStockAssertions.resourceStockExact(
                apiExecutor, storageId, UserRole.ADMIN, resourceId);
    }

    public double ensureStockAtLeast(Long storageId, Long resourceId, double minimum) {
        double current = getResourceStock(storageId, resourceId);
        if (current >= minimum) {
            return current;
        }
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, storageId, Map.of(resourceId, minimum - current));
        return getResourceStock(storageId, resourceId);
    }

    public Map<Long, Double> getInventorySnapshot(Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET, UserRole.ADMIN, String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(response, StorageItemResponse.class);
        Map<Long, Double> snapshot = new LinkedHashMap<>();
        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                snapshot.put(item.getResource().getId(), item.getAmount() != null ? item.getAmount() : 0.0);
            }
        }
        return snapshot;
    }

    public void assertInventoryUnchanged(Long storageId, Map<Long, Double> before) {
        assertThat(getInventorySnapshot(storageId)).isEqualTo(before);
    }

    public List<EquipmentCategoryResponse> getEquipmentCategories() {
        Response response = executeReadWithProjectRoleFallback(
                ApiEndpointDefinition.PROJECT_PRODUCTION_EQUIPMENT_CATEGORIES_GET);
        validateSuccess(response, "Get project-production equipment categories");
        return DatabaseIntegrityValidator.extractList(response, EquipmentCategoryResponse.class);
    }

    public List<SimpleEntityResponse> getEquipmentModels() {
        Response response = executeReadWithProjectRoleFallback(
                ApiEndpointDefinition.PROJECT_PRODUCTION_EQUIPMENT_MODELS_GET);
        validateSuccess(response, "Get project-production equipment models");
        return DatabaseIntegrityValidator.extractList(response, SimpleEntityResponse.class);
    }

    private Response executeReadWithProjectRoleFallback(ApiEndpointDefinition endpoint) {
        Response last = null;
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.OWNER_1, UserRole.PROJECT_MANAGER)) {
            last = apiExecutor.execute(endpoint, role);
            if (last.statusCode() >= 200 && last.statusCode() < 300) {
                return last;
            }
            log.debug("{} as {} returned {}", endpoint.name(), role, last.statusCode());
        }
        return last;
    }

    public Long findEquipmentModelId(String modelName) {
        return getEquipmentModels().stream()
                .filter(model -> modelName.equals(model.getName()))
                .map(SimpleEntityResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Equipment model not found: " + modelName));
    }

    public List<ProjectProductionEquipmentResponse> getAvailableEquipment(Long storageId, Long categoryId, Long modelId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_EQUIPMENTS_GET,
                UserRole.ADMIN, null, storageId, categoryId, modelId);
        validateSuccess(response, "Get equipment available for modification");
        return DatabaseIntegrityValidator.extractList(response, ProjectProductionEquipmentResponse.class);
    }

    public List<ProjectProductionParameterResponse> getEquipmentParameters(Long storageId, Long equipmentId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_PARAMETERS_GET,
                UserRole.ADMIN, null, storageId, equipmentId);
        validateSuccess(response, "Get project-production equipment parameters");
        return DatabaseIntegrityValidator.extractList(response, ProjectProductionParameterResponse.class);
    }

    public ProjectProductionResponse getById(Long id, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_GET_BY_ID, UserRole.ADMIN, null, id, storageId);
        validateSuccess(response, "Get project production by id");
        return response.as(ProjectProductionResponse.class);
    }

    public ProjectProductionResponse createAs(UserRole role, ProjectProductionRequest request) {
        Response response = createRaw(role, request);
        validateSuccess(response, "Create project production");
        return response.as(ProjectProductionResponse.class);
    }

    public Response createRaw(UserRole role, ProjectProductionRequest request) {
        return apiExecutor.executeProjectProductionCreate(request, role);
    }

    public ProjectProductionResponse createWithStageUsage(double amountNeeded, double amountUsed) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        ProjectProductionRequest request = ProjectProductionDataFactory.buildCreateRequest(
                storageId,
                testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID),
                testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID),
                ProjectProductionState.IN_PROGRESS,
                ProjectProductionType.CREATION,
                null);
        ProjectProductionResponse production = createAs(UserRole.ADMIN, request);
        addStage(UserRole.ADMIN, production.getId(), storageId,
                ProjectProductionDataFactory.singleResourceStage(
                        testContext.get(ContextKey.PROJECT_RESOURCE_ID), amountNeeded, amountUsed));
        return getById(production.getId(), storageId);
    }

    public ProjectProductionResponse updateAs(UserRole role, Long id, ProjectProductionRequest request) {
        Response response = updateRaw(role, id, request);
        validateSuccess(response, "Update project production");
        return response.as(ProjectProductionResponse.class);
    }

    public Response updateRaw(UserRole role, Long id, ProjectProductionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_PUT_UPDATE, role, request, id);
    }

    public void deleteAs(UserRole role, Long id, Long storageId, List<ResourceToRollbackRequest> rollbackBody) {
        Response response = deleteRaw(role, id, storageId, rollbackBody);
        validateSuccess(response, "Delete project production");
    }

    public Response deleteRaw(UserRole role, Long id, Long storageId, List<ResourceToRollbackRequest> rollbackBody) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_DELETE, role, rollbackBody, id, storageId);
    }

    public ProjectProductionStageResponse addStage(UserRole role, Long productionId, Long storageId,
                                                    ProjectProductionStageRequest request) {
        Response response = addStageRaw(role, productionId, storageId, request);
        validateSuccess(response, "Add project production stage");
        return response.as(ProjectProductionStageResponse.class);
    }

    public Response addStageRaw(UserRole role, Long productionId, Long storageId,
                                ProjectProductionStageRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_POST_ADD,
                role, request, productionId, storageId);
    }

    public ProjectProductionStageResponse updateStage(UserRole role, Long stageId, Long storageId,
                                                       ProjectProductionStageRequest request) {
        Response response = updateStageRaw(role, stageId, storageId, request);
        validateSuccess(response, "Update project production stage");
        return response.as(ProjectProductionStageResponse.class);
    }

    public Response updateStageRaw(UserRole role, Long stageId, Long storageId,
                                   ProjectProductionStageRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE,
                role, request, stageId, storageId);
    }

    public void finishAs(UserRole role, Long productionId, Long storageId) {
        Response response = finishRaw(role, productionId, storageId);
        validateSuccess(response, "Finish project production");
    }

    public Response finishRaw(UserRole role, Long productionId, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_FINISH, role, null, productionId, storageId);
    }

    public void cancelFinishedAs(UserRole role, Long productionId, Long storageId) {
        Response response = cancelFinishedRaw(role, productionId, storageId);
        validateSuccess(response, "Cancel finished project production");
    }

    public Response cancelFinishedRaw(UserRole role, Long productionId, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_CANCEL_FINISHED, role, null, productionId, storageId);
    }

    public ProjectProductionTemplateResponse createTemplate(UserRole role, ProjectProductionTemplateRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_POST_CREATE, role, request);
        validateSuccess(response, "Create project production template");
        return response.as(ProjectProductionTemplateResponse.class);
    }

    public ProjectProductionResponse createProductionFromTemplate(UserRole role, Long templateId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_CREATE_PRODUCTION,
                role, null, templateId, storageId);
        validateSuccess(response, "Create project production from template");
        return response.as(ProjectProductionResponse.class);
    }

    public ProjectProductionTemplateResponse createTemplateFromProduction(UserRole role, Long productionId,
                                                                          Long storageId, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_CREATE_TEMPLATE,
                role, null, productionId, storageId, name);
        validateSuccess(response, "Create template from existing project production");
        return response.as(ProjectProductionTemplateResponse.class);
    }
}
