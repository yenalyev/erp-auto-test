package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.request.UpdateNotesRequest;
import com.erp.enums.UserRole;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.validators.SchemaRegistry;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ProductionFixture extends BaseFixture {

    private static final double INPUT_STOCK = 100.0;

    private final TechnologicalMapFixture techMapFixture;
    private final ResourceFixture resourceFixture;

    public ProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів виробництва")
    public void prepareContext() {
        if (testContext.get(ContextKey.PRODUCTION_TECH_MAP) != null) {
            return;
        }

        // Unit + category for unique resource creation (do not reuse catalog shared resources —
        // staging often has deactivated first-page resources, and UPDATE/DELETE validate active).
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();

        Long storageId = ConfigProvider.getOwner1StorageId();
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse input1 = resourceFixture.createUniqueResource("PRD-IN1-" + suffix);
        ResourceResponse input2 = resourceFixture.createUniqueResource("PRD-IN2-" + suffix);
        ResourceResponse output = resourceFixture.createUniqueResource("PRD-OUT-" + suffix);
        List<ResourceResponse> isolatedResources = List.of(input1, input2, output);

        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionTechMap(isolatedResources, storageId)
                .build();
        TechnologicalMapResponse techMap = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);

        testContext.set(ContextKey.PRODUCTION_TECH_MAP, techMap);
        testContext.set(ContextKey.DYNAMIC_TECH_MAP, techMap);
        testContext.set(ContextKey.DYNAMIC_TECH_MAP_ID, techMap.getId());
        testContext.set(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS, List.of(input1.getId(), input2.getId()));
        testContext.set(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID, output.getId());

        seedStockViaRelocation(storageId, input1.getId(), input2.getId());
        log.info("Production fixture ready: techMap={}, storage={}, inputs=[{}, {}], outputResource={}",
                techMap.getId(), storageId, input1.getId(), input2.getId(), output.getId());
    }

    @Step("API: GET журнал виробництва (сторінка {query.page}, size={query.pageSize})")
    public List<ManufacturingItemResponse> getJournalPage(ProductionJournalQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_GET_JOURNAL_PAGE,
                UserRole.OWNER_1,
                query.toQueryParams());
        validateSuccess(response, "Get production journal page");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.PRODUCTION_GET_JOURNAL_PAGE);
        return DatabaseIntegrityValidator.extractList(response, ManufacturingItemResponse.class);
    }

    @Step("API: GET totalElements журналу виробництва")
    public long getJournalTotalElements(ProductionJournalQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_GET_JOURNAL_PAGE,
                UserRole.OWNER_1,
                query.toQueryParams());
        validateSuccess(response, "Get production journal total elements");
        return DatabaseIntegrityValidator.extractPageTotalElements(response);
    }

    @Step("API: GET категорії ресурсів")
    public List<ResourceCategoryResponse> getResourceCategories() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.OWNER_1);
        validateSuccess(response, "Get resource categories");
        return DatabaseIntegrityValidator.extractList(response, ResourceCategoryResponse.class);
    }

    @Step("API: Map productId → categoryId з довідника ресурсів")
    public Map<Long, Long> getProductCategoryMap() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_GET_ALL, UserRole.OWNER_1);
        validateSuccess(response, "Get resources for category map");

        boolean paged = response.getBody().asString().stripLeading().startsWith("{");
        String listPath = paged ? "content" : "$";
        List<Object> resources = response.jsonPath().getList(listPath);
        if (resources == null || resources.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> productCategoryMap = new LinkedHashMap<>();
        String itemPrefix = paged ? "content[%d]" : "[%d]";
        for (int i = 0; i < resources.size(); i++) {
            String prefix = String.format(itemPrefix, i);
            Long resourceId = response.jsonPath().getLong(prefix + ".id");
            Long categoryId = response.jsonPath().getLong(prefix + ".category.id");
            if (resourceId != null && categoryId != null) {
                productCategoryMap.put(resourceId, categoryId);
            }
        }
        return productCategoryMap;
    }

    @Step("API: GET production id={productionId}")
    public ManufacturingItemResponse getById(UserRole role, Long productionId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_GET_BY_ID,
                role,
                null,
                productionId,
                storageId);
        validateSuccess(response, "Get production id=" + productionId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.PRODUCTION_GET_BY_ID);
        return response.as(ManufacturingItemResponse.class);
    }

    @Step("API: PATCH notes для виробництва {productionId} на локації {storageId}")
    public ManufacturingItemResponse updateNotes(UserRole role,
                                                 Long productionId,
                                                 Long storageId,
                                                 String notes) {
        UpdateNotesRequest request = UpdateNotesRequest.builder().notes(notes).build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_PATCH_NOTES,
                role,
                request,
                productionId,
                storageId);
        validateSuccess(response, "Patch production notes id=" + productionId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.PRODUCTION_PATCH_NOTES);
        return response.as(ManufacturingItemResponse.class);
    }

    @Step("API: GET tag-statistics для журналу виробництва")
    public List<ProductionProcessTagStatisticResponse> getTagStatistics(ProductionJournalQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_TAG_STATISTICS_GET,
                UserRole.OWNER_1,
                query.toQueryParams());
        validateSuccess(response, "Get production tag statistics");
        return DatabaseIntegrityValidator.extractList(response, ProductionProcessTagStatisticResponse.class);
    }

    @Step("API: GET каталог production-process-tags для storageId={storageId}")
    public Collection<String> getProductionProcessTags(long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.APP_CONFIG_PRODUCTION_PROCESS_TAGS_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        validateSuccess(response, "Get production process tags catalog");
        List<String> tags = response.jsonPath().getList("$", String.class);
        return tags != null ? tags : List.of();
    }

    @Step("API: створити виробництво з унікальною партією")
    public ManufacturingItemResponse createWithUniqueBatch(UserRole role,
                                                           Long storageId,
                                                           TechnologicalMapResponse techMap,
                                                           double amount) {
        return createAs(role, storageId, techMap, amount, ProductionDataFactory.uniqueBatchNumber());
    }

    public TechnologicalMapFixture getTechMapFixture() {
        return techMapFixture;
    }

    @Step("API: створити виробництво — {amount} од., партія «{batchNumber}»")
    public ManufacturingItemResponse createAs(UserRole role,
                                              Long storageId,
                                              TechnologicalMapResponse techMap,
                                              double amount,
                                              String batchNumber) {
        return createAs(role, storageId, techMap, amount, batchNumber, java.time.LocalDate.now());
    }

    @Step("API: створити виробництво — {amount} од., партія «{batchNumber}», дата {date}")
    public ManufacturingItemResponse createAs(UserRole role,
                                              Long storageId,
                                              TechnologicalMapResponse techMap,
                                              double amount,
                                              String batchNumber,
                                              java.time.LocalDate date) {
        var request = com.erp.data.factories.production.ProductionDataFactory
                .buildCreateRequest(techMap, amount, date, batchNumber);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                role,
                request,
                String.valueOf(storageId));
        validateSuccess(response, "Create production batch=" + batchNumber);
        List<ManufacturingItemResponse> created = response.jsonPath()
                .getList("", ManufacturingItemResponse.class);
        if (created == null || created.isEmpty()) {
            throw new IllegalStateException("Empty create production response");
        }
        return created.getFirst();
    }

    @Step("API: оновити виробництво id={productionId} — новий обсяг {amount} од., партія «{batchNumber}»")
    public ManufacturingItemResponse updateAs(UserRole role,
                                              Long productionId,
                                              Long storageId,
                                              TechnologicalMapResponse techMap,
                                              double amount,
                                              String batchNumber) {
        Response response = updateRaw(role, productionId, storageId, techMap, amount, batchNumber);
        validateSuccess(response, "Update production id=" + productionId);
        return response.as(ManufacturingItemResponse.class);
    }

    @Step("API: PUT production (raw response) id={productionId}")
    public Response updateRaw(UserRole role,
                              Long productionId,
                              Long storageId,
                              TechnologicalMapResponse techMap,
                              double amount,
                              String batchNumber) {
        var request = com.erp.data.factories.production.ProductionDataFactory
                .buildCreateRequest(techMap, amount, java.time.LocalDate.now(), batchNumber);
        return apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_PUT_UPDATE,
                role,
                request,
                productionId,
                storageId);
    }

    @Step("API: видалити виробництво id={productionId} зі складу {storageId}")
    public void deleteAs(UserRole role, Long productionId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_DELETE,
                role,
                null,
                productionId,
                storageId);
        validateSuccess(response, "Delete production id=" + productionId);
    }

    @Step("FIXTURE: Забезпечити мінімальний запас сировини на складі {storageId}")
    public void ensureInputStockAtLeast(Long storageId, Long input1, Long input2, double minimum) {
        topUpIfNeeded(storageId, input1, minimum);
        topUpIfNeeded(storageId, input2, minimum);
    }

    @Step("FIXTURE: Забезпечити мінімальний запас для ресурсів техкарти (fixed + alternatives) на складі {storageId}")
    public void ensureStockForTechMapInputs(Long storageId, TechnologicalMapResponse techMap, double minimum) {
        Map<Long, Double> amounts = new LinkedHashMap<>();
        if (techMap.getInput() != null) {
            for (var usage : techMap.getInput()) {
                if (usage.getResource() != null) {
                    amounts.put(usage.getResource().getId(), minimum);
                }
            }
        }
        if (techMap.getGroups() != null) {
            for (var group : techMap.getGroups()) {
                if (group.getAlternativeResources() == null) {
                    continue;
                }
                for (var alt : group.getAlternativeResources()) {
                    if (alt.getResource() != null) {
                        amounts.put(alt.getResource().getId(), minimum);
                    }
                }
            }
        }
        for (Map.Entry<Long, Double> entry : amounts.entrySet()) {
            topUpIfNeeded(storageId, entry.getKey(), entry.getValue());
        }
    }

    @Step("API: створити виробництво з явним вибором alternativeInputs")
    public ManufacturingItemResponse createAsWithAlternatives(
            UserRole role,
            Long storageId,
            TechnologicalMapResponse techMap,
            double amount,
            List<com.erp.models.request.AlternativeInputRequest> alternativeInputs) {
        var request = ProductionDataFactory.buildCreateRequest(
                techMap, amount, java.time.LocalDate.now(),
                ProductionDataFactory.uniqueBatchNumber(), alternativeInputs);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                role,
                request,
                String.valueOf(storageId));
        validateSuccess(response, "Create production with alternativeInputs");
        List<ManufacturingItemResponse> created = response.jsonPath()
                .getList("", ManufacturingItemResponse.class);
        if (created == null || created.isEmpty()) {
            throw new IllegalStateException("Empty create production response");
        }
        return created.getFirst();
    }

    @Step("API: спроба створити виробництво (очікується помилка валідації)")
    public Response tryCreateAs(UserRole role,
                                Long storageId,
                                com.erp.models.request.ManufacturingListRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                role,
                request,
                String.valueOf(storageId));
    }

    @Step("API: Отримати залишок ресурсу {resourceId} на складі {storageId}")
    public double getResourceStock(Long storageId, Long resourceId) {
        return ProductionStockAssertions.resourceStockExact(apiExecutor, storageId, UserRole.OWNER_1, resourceId);
    }

    private void topUpIfNeeded(Long storageId, Long resourceId, double minimum) {
        double current = getResourceStock(storageId, resourceId);
        if (current >= minimum) {
            return;
        }
        double toAdd = minimum - current;
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.ADMIN,
                storageId,
                Map.of(resourceId, toAdd));
        double after = getResourceStock(storageId, resourceId);
        log.info("Topped up resource {} on storage {}: {} → {}", resourceId, storageId, current, after);
    }

    @Step("FIXTURE: Seed input stock via relocation receive (SUPPLIER → storage {storageId})")
    private void seedStockViaRelocation(Long storageId, Long input1, Long input2) {
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.ADMIN,
                storageId,
                Map.of(input1, INPUT_STOCK, input2, INPUT_STOCK));
        log.info("Seeded stock via relocation receive: storage={}, resources=[{}, {}], amount={}",
                storageId, input1, input2, INPUT_STOCK);
    }
}
