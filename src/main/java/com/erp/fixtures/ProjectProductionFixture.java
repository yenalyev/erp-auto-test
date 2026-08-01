package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.models.request.ProjectCategoryRequest;
import com.erp.models.request.ProjectProductRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionStageRequest;
import com.erp.models.request.ProjectProductionTemplateRequest;
import com.erp.models.request.ResourceToRollbackRequest;
import com.erp.models.response.ProjectCategoryResponse;
import com.erp.models.response.ProjectProductInstanceResponse;
import com.erp.models.response.ProjectProductResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ProjectProductionStageResponse;
import com.erp.models.response.ProjectProductionTemplateResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture for the Project Production domain
 * ({@code /api/v1/project-production}, {@code /api/v1/project-production-template},
 * {@code /api/v1/project-category}, {@code /api/v1/project-product}).
 * Mirrors {@link NonSeriesProductionFixture} conventions.
 */
@Slf4j
public class ProjectProductionFixture extends BaseFixture {

    public static final double DEFAULT_SEEDED_STOCK = 50.0;

    public ProjectProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів проєктного виробництва")
    public void prepareContext() {
        if (testContext.get(ContextKey.PROJECT_CATEGORY_ID) != null) {
            return;
        }

        ensureProjectUsers();

        Long storageId = ConfigProvider.getOwner1StorageId();
        ResourceResponse resource = resolveResourceForStorage(storageId);

        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(resource.getId(), DEFAULT_SEEDED_STOCK));

        double stockAfterSeed = getResourceStock(storageId, resource.getId());

        CatalogPair catalog = resolveCatalogPreferReuse();

        testContext.set(ContextKey.PROJECT_CATEGORY_ID, catalog.category().getId());
        testContext.set(ContextKey.PROJECT_CATEGORY_NAME, catalog.category().getName());
        testContext.set(ContextKey.PROJECT_PRODUCT_ID, catalog.product().getId());
        testContext.set(ContextKey.PROJECT_PRODUCT_NAME, catalog.product().getName());
        testContext.set(ContextKey.PROJECT_RESOURCE_ID, resource.getId());
        testContext.set(ContextKey.PROJECT_RESOURCE_NAME, resource.getName());
        testContext.set(ContextKey.PROJECT_SEEDED_STOCK, stockAfterSeed);

        ProjectProductionResponse shared = createAs(UserRole.PROJECT_MANAGER, ProjectProductionDataFactory.buildCreateRequest(
                storageId, catalog.category().getId(), catalog.product().getId(),
                ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null));
        testContext.set(ContextKey.PROJECT_PRODUCTION_ID, shared.getId());

        log.info("Project production fixture ready: storage={}, category={}, product={}, resource={} ({}), stock={}, catalogSource={}",
                storageId, catalog.category().getId(), catalog.product().getId(),
                resource.getId(), resource.getName(), stockAfterSeed, catalog.source());
    }

    @Step("FIXTURE: Ensure projectprod / projectprodab users via ADMIN")
    private void ensureProjectUsers() {
        UserFixture userFixture = new UserFixture(testContext, apiExecutor);
        PlaywrightSessionProvider playwright = resolvePlaywright();
        userFixture.ensureProjectProductionUsers(playwright);
    }

    private PlaywrightSessionProvider resolvePlaywright() {
        try {
            // BaseTest exposes static provider used by AuthService for remote envs
            Class<?> baseTest = Class.forName("com.erp.tests.BaseTest");
            java.lang.reflect.Method getter = baseTest.getDeclaredMethod("getPlaywrightSessionProvider");
            getter.setAccessible(true);
            Object provider = getter.invoke(null);
            return provider instanceof PlaywrightSessionProvider p ? p : null;
        } catch (Exception e) {
            log.warn("Could not resolve PlaywrightSessionProvider for user bootstrap: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Prefer reusing an existing active category that already has a product — avoids polluting
     * the catalog and works when create is forbidden (403 on staging for OWNER_1/ADMIN).
     * Create via PROJECT_ADMIN only if the catalog is empty.
     */
    @Step("FIXTURE: Resolve project category+product (reuse first)")
    private CatalogPair resolveCatalogPreferReuse() {
        List<ProjectCategoryResponse> categories = listActiveCategories();
        for (ProjectCategoryResponse category : categories) {
            Optional<ProjectProductResponse> product = findFirstProductForCategory(category.getId());
            if (product.isPresent()) {
                log.info("Reusing existing catalog: categoryId={}, productId={} ({})",
                        category.getId(), product.get().getId(), product.get().getName());
                return new CatalogPair(category, product.get(), "reuse");
            }
        }

        if (!categories.isEmpty()) {
            ProjectCategoryResponse category = categories.getFirst();
            log.info("Active categories exist but have no products — creating product under category {}",
                    category.getId());
            ProjectProductResponse product = createProductOrFail(category.getId());
            return new CatalogPair(category, product, "reuse-category+create-product");
        }

        log.info("No active project categories — creating category + product via PROJECT_ADMIN");
        ProjectCategoryResponse category = createCategoryOrFail();
        ProjectProductResponse product = createProductOrFail(category.getId());
        return new CatalogPair(category, product, "create");
    }

    private record CatalogPair(ProjectCategoryResponse category, ProjectProductResponse product, String source) {}

    private ProjectCategoryResponse createCategoryOrFail() {
        ProjectCategoryRequest request = ProjectProductionDataFactory.buildCategoryCreateRequest();
        Response response = createCategoryRaw(UserRole.PROJECT_ADMIN, request);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.as(ProjectCategoryResponse.class);
        }
        throw new IllegalStateException(
                "No project category to reuse and create failed (status=" + response.statusCode()
                        + "). Seed an active category or grant Project-Production-ROLE create permission.");
    }

    private ProjectProductResponse createProductOrFail(Long categoryId) {
        ProjectProductRequest request = ProjectProductionDataFactory.buildProductCreateRequest(categoryId);
        Response response = createProductRaw(UserRole.PROJECT_ADMIN, request);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.as(ProjectProductResponse.class);
        }
        throw new IllegalStateException(
                "No project product to reuse for category " + categoryId
                        + " and create failed (status=" + response.statusCode()
                        + "). Seed a product or grant Project-Production-ROLE create permission.");
    }

    private List<ProjectCategoryResponse> listActiveCategories() {
        for (UserRole role : List.of(UserRole.PROJECT_MANAGER, UserRole.PROJECT_ADMIN, UserRole.OWNER_1)) {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.PROJECT_CATEGORY_GET_ALL_ACTIVE, role);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DatabaseIntegrityValidator.extractList(response, ProjectCategoryResponse.class);
            }
            log.debug("GET active project categories as {} → {}", role, response.statusCode());
        }
        return List.of();
    }

    private Optional<ProjectProductResponse> findFirstProductForCategory(Long categoryId) {
        for (UserRole role : List.of(UserRole.PROJECT_MANAGER, UserRole.PROJECT_ADMIN, UserRole.OWNER_1)) {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.PROJECT_PRODUCT_GET_ALL_BY_CATEGORY, role, null, categoryId);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<ProjectProductResponse> list = DatabaseIntegrityValidator.extractList(
                        response, ProjectProductResponse.class);
                return list.stream()
                        .filter(p -> p.getActive() == null || Boolean.TRUE.equals(p.getActive()))
                        .findFirst()
                        .or(() -> list.stream().findFirst());
            }
            log.debug("GET products by category {} as {} → {}", categoryId, role, response.statusCode());
        }
        return Optional.empty();
    }

    /**
     * Picks a resource already present on the target storage inventory (fast path for UI tests).
     * Falls back to creating a single resource without loading the full resources catalog.
     */
    @Step("FIXTURE: Resolve resource for storage {storageId}")
    private ResourceResponse resolveResourceForStorage(Long storageId) {
        Response invResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                invResponse, StorageItemResponse.class);

        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                log.info("Using resource from storage inventory: id={}, name={}",
                        item.getResource().getId(), item.getResource().getName());
                return item.getResource();
            }
        }

        log.info("Storage {} has no inventory items — creating a single resource", storageId);
        fetchSharedUnit(1);
        fetchSharedResourceCategory();
        setupSharedResource();
        ResourceResponse created = testContext.get(ContextKey.SHARED_RESOURCE);
        if (created == null) {
            throw new IllegalStateException("Failed to create resource for project production fixture");
        }
        return created;
    }

    // ═══════════════════════════════════════════════════════════════
    // STOCK HELPERS
    // ═══════════════════════════════════════════════════════════════

    @Step("API: Отримати залишок ресурсу {resourceId} на складі {storageId}")
    public double getResourceStock(Long storageId, Long resourceId) {
        return ProductionStockAssertions.resourceStockExact(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId);
    }

    @Step("FIXTURE: Ensure stock ≥ {minimum} for resource {resourceId} on storage {storageId}")
    public double ensureStockAtLeast(Long storageId, Long resourceId, double minimum) {
        double current = getResourceStock(storageId, resourceId);
        if (current >= minimum) {
            return current;
        }
        double toAdd = minimum - current;
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(resourceId, toAdd));
        double after = getResourceStock(storageId, resourceId);
        log.info("Topped up stock for resource {}: {} → {}", resourceId, current, after);
        return after;
    }

    @Step("API: Знімок залишків усіх ресурсів на складі {storageId}")
    public Map<Long, Double> getInventorySnapshot(Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                response, StorageItemResponse.class);

        Map<Long, Double> snapshot = new LinkedHashMap<>();
        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                snapshot.put(item.getResource().getId(),
                        item.getAmount() != null ? item.getAmount() : 0.0);
            }
        }
        return snapshot;
    }

    @Step("API: Перевірити, що залишки на складі {storageId} не змінились")
    public void assertInventoryUnchanged(Long storageId, Map<Long, Double> before) {
        Map<Long, Double> after = getInventorySnapshot(storageId);
        assertThat(after)
                .as("Знімок залишків після операції має збігатися зі знімком до неї")
                .isEqualTo(before);
    }

    // ═══════════════════════════════════════════════════════════════
    // PROJECT PRODUCTION CRUD
    // ═══════════════════════════════════════════════════════════════

    @Step("API: GET project production /{id}?storageId={storageId}")
    public ProjectProductionResponse getById(Long id, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_GET_BY_ID, UserRole.PROJECT_MANAGER, null, id, storageId);
        validateSuccess(response, "Get project production by id");
        return response.as(ProjectProductionResponse.class);
    }

    /** Multipart create — no stages in the body; stock deduction happens via {@link #addStage}. */
    @Step("API: Створити проєктне виробництво")
    public ProjectProductionResponse createAs(UserRole role, ProjectProductionRequest request) {
        Response response = createRaw(role, request);
        validateSuccess(response, "Create project production");
        return response.as(ProjectProductionResponse.class);
    }

    @Step("API: POST project production (raw response)")
    public Response createRaw(UserRole role, ProjectProductionRequest request) {
        return apiExecutor.executeProjectProductionCreate(request, role);
    }

    /**
     * Convenience: creates an IN_PROGRESS/CREATION production, then adds a single stage
     * with the given resource usage — this is the path that actually deducts stock
     * (see {@code ProjectProductionStageFacade#addStage}; the initial create only persists stages,
     * it never calls {@code storageItemService.produce}).
     */
    @Step("FIXTURE: Створити проєктне виробництво зі стадією (needed={amountNeeded}, used={amountUsed})")
    public ProjectProductionResponse createWithStageUsage(double amountNeeded, double amountUsed) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        Long categoryId = testContext.get(ContextKey.PROJECT_CATEGORY_ID);
        Long productId = testContext.get(ContextKey.PROJECT_PRODUCT_ID);
        Long resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);

        ProjectProductionRequest createRequest = ProjectProductionDataFactory.buildCreateRequest(
                storageId, categoryId, productId,
                ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null);
        ProjectProductionResponse production = createAs(UserRole.PROJECT_MANAGER, createRequest);

        addStage(UserRole.PROJECT_MANAGER, production.getId(), storageId,
                ProjectProductionDataFactory.singleResourceStage(resourceId, amountNeeded, amountUsed));

        return getById(production.getId(), storageId);
    }

    @Step("API: Оновити проєктне виробництво /{id}")
    public ProjectProductionResponse updateAs(UserRole role, Long id, ProjectProductionRequest request) {
        Response response = updateRaw(role, id, request);
        validateSuccess(response, "Update project production");
        return response.as(ProjectProductionResponse.class);
    }

    @Step("API: PUT project production (raw response)")
    public Response updateRaw(UserRole role, Long id, ProjectProductionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_PUT_UPDATE, role, request, id);
    }

    /** {@code rollbackBody} may be null (no rollback), empty (full auto-rollback) or partial amounts. */
    @Step("API: Видалити проєктне виробництво /{id}")
    public void deleteAs(UserRole role, Long id, Long storageId, List<ResourceToRollbackRequest> rollbackBody) {
        Response response = deleteRaw(role, id, storageId, rollbackBody);
        validateSuccess(response, "Delete project production");
    }

    @Step("API: DELETE project production (raw response)")
    public Response deleteRaw(UserRole role, Long id, Long storageId, List<ResourceToRollbackRequest> rollbackBody) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_DELETE, role, rollbackBody, id, storageId);
    }

    // ═══════════════════════════════════════════════════════════════
    // STAGES
    // ═══════════════════════════════════════════════════════════════

    /** Deducts stock for the stage's resource usages (amountUsed) via {@code storageItemService.produce}. */
    @Step("API: Додати стадію до проєктного виробництва {productionId}")
    public ProjectProductionStageResponse addStage(UserRole role, Long productionId, Long storageId,
                                                    ProjectProductionStageRequest stageRequest) {
        Response response = addStageRaw(role, productionId, storageId, stageRequest);
        validateSuccess(response, "Add project production stage");
        return response.as(ProjectProductionStageResponse.class);
    }

    @Step("API: POST project production stage (raw response)")
    public Response addStageRaw(UserRole role, Long productionId, Long storageId,
                                ProjectProductionStageRequest stageRequest) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_POST_ADD, role, stageRequest, productionId, storageId);
    }

    @Step("API: Оновити стадію проєктного виробництва {stageId}")
    public ProjectProductionStageResponse updateStage(UserRole role, Long stageId, Long storageId,
                                                       ProjectProductionStageRequest stageRequest) {
        Response response = updateStageRaw(role, stageId, storageId, stageRequest);
        validateSuccess(response, "Update project production stage");
        return response.as(ProjectProductionStageResponse.class);
    }

    @Step("API: PUT project production stage (raw response)")
    public Response updateStageRaw(UserRole role, Long stageId, Long storageId,
                                   ProjectProductionStageRequest stageRequest) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE, role, stageRequest, stageId, storageId);
    }

    // ═══════════════════════════════════════════════════════════════
    // FINISH / CANCEL
    // ═══════════════════════════════════════════════════════════════

    @Step("API: Завершити проєктне виробництво {productionId}")
    public void finishAs(UserRole role, Long productionId, Long storageId) {
        Response response = finishRaw(role, productionId, storageId);
        validateSuccess(response, "Finish project production");
    }

    @Step("API: PUT finish-project (raw response)")
    public Response finishRaw(UserRole role, Long productionId, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_FINISH, role, null, productionId, storageId);
    }

    @Step("API: Скасувати завершення проєктного виробництва {productionId}")
    public void cancelFinishedAs(UserRole role, Long productionId, Long storageId) {
        Response response = cancelFinishedRaw(role, productionId, storageId);
        validateSuccess(response, "Cancel finished project production");
    }

    @Step("API: PUT cancel-finished-project (raw response)")
    public Response cancelFinishedRaw(UserRole role, Long productionId, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_CANCEL_FINISHED, role, null, productionId, storageId);
    }

    // ═══════════════════════════════════════════════════════════════
    // TEMPLATES
    // ═══════════════════════════════════════════════════════════════

    @Step("API: Створити шаблон проєктного виробництва")
    public ProjectProductionTemplateResponse createTemplate(UserRole role, ProjectProductionTemplateRequest request) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_POST_CREATE, role, request);
        validateSuccess(response, "Create project production template");
        return response.as(ProjectProductionTemplateResponse.class);
    }

    @Step("API: Створити проєктне виробництво з шаблону {templateId}")
    public ProjectProductionResponse createProductionFromTemplate(UserRole role, Long templateId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_CREATE_PRODUCTION, role, null, templateId, storageId);
        validateSuccess(response, "Create project production from template");
        return response.as(ProjectProductionResponse.class);
    }

    @Step("API: Зберегти проєктне виробництво {productionId} як шаблон")
    public ProjectProductionTemplateResponse createTemplateFromProduction(UserRole role, Long productionId,
                                                                          Long storageId, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_CREATE_TEMPLATE, role, null, productionId, storageId, name);
        validateSuccess(response, "Create template from existing project production");
        return response.as(ProjectProductionTemplateResponse.class);
    }

    // ═══════════════════════════════════════════════════════════════
    // CATEGORY / PRODUCT CATALOG
    // ═══════════════════════════════════════════════════════════════

    @Step("API: Створити проєктну категорію")
    public ProjectCategoryResponse createCategory(UserRole role, ProjectCategoryRequest request) {
        Response response = createCategoryRaw(role, request);
        validateSuccess(response, "Create project category");
        return response.as(ProjectCategoryResponse.class);
    }

    @Step("API: POST project category (raw response)")
    public Response createCategoryRaw(UserRole role, ProjectCategoryRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_CATEGORY_POST_CREATE, role, request);
    }

    @Step("API: Оновити проєктну категорію /{id}")
    public ProjectCategoryResponse updateCategory(UserRole role, Long id, ProjectCategoryRequest request) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_CATEGORY_PUT_UPDATE, role, request, id);
        validateSuccess(response, "Update project category");
        return response.as(ProjectCategoryResponse.class);
    }

    @Step("API: Видалити (деактивувати) проєктну категорію /{id}")
    public void deleteCategory(UserRole role, Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_CATEGORY_DELETE, role, null, id);
        validateSuccess(response, "Delete project category");
    }

    @Step("API: Відновити проєктну категорію /{id}/restore")
    public void restoreCategory(UserRole role, Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_CATEGORY_PUT_RESTORE, role, null, id);
        validateSuccess(response, "Restore project category");
    }

    @Step("API: GET project category /{id}")
    public ProjectCategoryResponse getCategoryById(Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_CATEGORY_GET_BY_ID, UserRole.PROJECT_ADMIN, null, id);
        validateSuccess(response, "Get project category by id");
        return response.as(ProjectCategoryResponse.class);
    }

    @Step("API: Створити проєктний продукт")
    public ProjectProductResponse createProduct(UserRole role, ProjectProductRequest request) {
        Response response = createProductRaw(role, request);
        validateSuccess(response, "Create project product");
        return response.as(ProjectProductResponse.class);
    }

    @Step("API: POST project product (raw response)")
    public Response createProductRaw(UserRole role, ProjectProductRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCT_POST_CREATE, role, request);
    }

    @Step("API: Оновити проєктний продукт /{id}")
    public ProjectProductResponse updateProduct(UserRole role, Long id, ProjectProductRequest request) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCT_PUT_UPDATE, role, request, id);
        validateSuccess(response, "Update project product");
        return response.as(ProjectProductResponse.class);
    }

    @Step("API: Видалити (деактивувати) проєктний продукт /{id}")
    public void deleteProduct(UserRole role, Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCT_DELETE, role, null, id);
        validateSuccess(response, "Delete project product");
    }

    @Step("API: Відновити проєктний продукт /{id}/restore")
    public void restoreProduct(UserRole role, Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCT_PUT_RESTORE, role, null, id);
        validateSuccess(response, "Restore project product");
    }

    @Step("API: GET project product /{id}")
    public ProjectProductResponse getProductById(Long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCT_GET_BY_ID, UserRole.PROJECT_ADMIN, null, id);
        validateSuccess(response, "Get project product by id");
        return response.as(ProjectProductResponse.class);
    }

    // ═══════════════════════════════════════════════════════════════
    // FINISHED BATCH LOOKUP (products endpoint + storage-item batches)
    // ═══════════════════════════════════════════════════════════════

    /**
     * {@code GET /api/v1/project-production/products?storageId=&category=} — {@code category} is
     * the project PRODUCT's name (see {@code ProjectProductionService#getOrBuildProjectResource}:
     * the auto-created resource is named after {@code projectProduction.getProjectProduct().getName()}).
     */
    @Step("API: GET project production products (category={projectProductName})")
    public List<ProjectProductInstanceResponse> getProducts(Long storageId, String projectProductName) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_PRODUCTS_GET, UserRole.PROJECT_MANAGER, null, storageId, projectProductName);
        validateSuccess(response, "Get project production products");
        return DatabaseIntegrityValidator.extractList(response, ProjectProductInstanceResponse.class);
    }

    @Step("FIXTURE: Перевірити наявність партії з серійним номером {serialNumber}")
    public boolean hasFinishedBatch(Long storageId, String projectProductName, String serialNumber) {
        return getProducts(storageId, projectProductName).stream()
                .anyMatch(instance -> serialNumber.equals(instance.getSerialNumber()));
    }

    /** Cross-checks the products endpoint with the underlying storage-item batch (amount, isProduced). */
    @Step("FIXTURE: Знайти партію готової продукції за серійним номером {serialNumber}")
    public Optional<StorageItemBatchResponse> findFinishedBatch(Long storageId, String projectProductName,
                                                                 String serialNumber) {
        Long resourceId = getProducts(storageId, projectProductName).stream()
                .filter(instance -> serialNumber.equals(instance.getSerialNumber()))
                .map(ProjectProductInstanceResponse::getResourceId)
                .findFirst()
                .orElse(null);
        if (resourceId == null) {
            return Optional.empty();
        }
        return ProductionStockAssertions.findBatch(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId, serialNumber, true);
    }
}
