package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.ExecutionFilterRequest;
import com.erp.models.request.PlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.NeededResourceResponse;
import com.erp.models.response.PlanNeededResourcesResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Isolated PRODUCTION BOM + plan/stock helpers for {@code POST /statistics/needed-resources}.
 */
@Slf4j
public class PlanNeededResourcesFixture extends BaseFixture {

    public static final double PLAN_GOAL = 100.0;
    public static final double PRODUCED = 30.0;
    public static final double INTERMEDIATE_PER_PRODUCT = 2.0;
    public static final double RAW_PER_INTERMEDIATE = 3.0;
    public static final double INTERMEDIATE_STOCK = 40.0;
    public static final double RAW_STOCK = 50.0;

    private final TechnologicalMapFixture techMapFixture;
    private final ProductionFixture productionFixture;
    private final ResourceFixture resourceFixture;
    private final InventoryFixture inventoryFixture;

    public PlanNeededResourcesFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        this.productionFixture = new ProductionFixture(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
        this.inventoryFixture = new InventoryFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: довідники для needed-resources")
    public void prepareContext() {
        techMapFixture.prepareContext();
    }

    public TechnologicalMapFixture techMaps() {
        return techMapFixture;
    }

    public ProductionFixture production() {
        return productionFixture;
    }

    @Step("Створити дворівневий ланцюжок product←intermediate←raw на локаціях {storageIds}")
    public Chain createTwoLevelChain(Set<Long> storageIds, Long intermediateCategoryId, Long rawCategoryId) {
        if (storageIds == null || storageIds.isEmpty()) {
            throw new IllegalArgumentException("storageIds is required");
        }
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse raw = resourceFixture.createUniqueResource("NR-RAW-" + suffix, rawCategoryId);
        ResourceResponse intermediate = resourceFixture.createUniqueResource(
                "NR-INT-" + suffix, intermediateCategoryId);
        ResourceResponse product = resourceFixture.createUniqueResource("NR-OUT-" + suffix);

        TechnologicalMapResponse intermediateMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-int-map",
                        List.of(new ResourceUsageRequest(raw.getId(), RAW_PER_INTERMEDIATE)),
                        List.of(new ResourceUsageRequest(intermediate.getId(), 1.0)),
                        storageIds).build());
        TechnologicalMapResponse productMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-prd-map",
                        List.of(new ResourceUsageRequest(intermediate.getId(), INTERMEDIATE_PER_PRODUCT)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        storageIds).build());
        return Chain.builder()
                .product(product)
                .intermediate(intermediate)
                .raw(raw)
                .productMap(productMap)
                .intermediateMap(intermediateMap)
                .build();
    }

    public Chain createTwoLevelChain(Long storageId) {
        Long categoryId = testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        return createTwoLevelChain(Set.of(storageId), categoryId, categoryId);
    }

    @Step("API: план {period} {amount} од. ресурсу {resourceId} на складі {storageId}")
    public PlanResponse createPlan(Long storageId, Long resourceId, YearMonth period, double amount) {
        return techMapFixture.createLocationPlan(storageId, resourceId, period, amount);
    }

    public PlanResponse createCurrentMonthPlan(Long storageId, Long resourceId, double amount) {
        return createPlan(storageId, resourceId, YearMonth.now(), amount);
    }

    @Step("API: план поточного місяця з кількома виробами на складі {storageId}")
    public PlanResponse createCurrentMonthPlan(Long storageId, List<ResourceUsageRequest> outputs) {
        YearMonth period = YearMonth.now();
        PlanRequest request = PlanRequest.builder()
                .description("NR multi-output")
                .storageId(storageId)
                .month(period.getMonthValue())
                .year(period.getYear())
                .output(outputs)
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Create location plan with " + outputs.size() + " outputs");
        return response.as(PlanResponse.class);
    }

    @Step("Посіяти залишок {amount} ресурсу {resourceId} на складі {storageId}")
    public void seedStock(Long storageId, Long resourceId, double amount) {
        if (amount <= 0) {
            return;
        }
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, storageId, Map.of(resourceId, amount));
    }

    @Step("Виставити точний залишок {amount} ресурсу {resourceId} на складі {storageId}")
    public void setExactStock(Long storageId, Long resourceId, double amount) {
        double current = com.erp.utils.helpers.ProductionStockAssertions.resourceStockExact(
                apiExecutor, storageId, UserRole.ADMIN, resourceId);
        if (current <= 0 && amount > 0) {
            seedStock(storageId, resourceId, amount);
            current = com.erp.utils.helpers.ProductionStockAssertions.resourceStockExact(
                    apiExecutor, storageId, UserRole.ADMIN, resourceId);
        }
        if (Math.abs(current - amount) < 0.001) {
            return;
        }
        inventoryFixture.resetResourceStock(storageId, resourceId, amount, UserRole.ADMIN);
    }

    public ResourceResponse createResource(String namePrefix) {
        return resourceFixture.createUniqueResource(namePrefix);
    }

    public ResourceResponse createResource(String namePrefix, Long categoryId) {
        return resourceFixture.createUniqueResource(namePrefix, categoryId);
    }

    @Step("Виготовити {amount} од. за техкартою на складі {storageId}")
    public ManufacturingItemResponse produce(Long storageId, TechnologicalMapResponse techMap, double amount) {
        Map<Long, Double> inputs = new java.util.HashMap<>();
        if (techMap.getInput() != null) {
            techMap.getInput().forEach(usage ->
                    inputs.put(usage.getResource().getId(), usage.getAmount() * amount * 2));
        }
        if (!inputs.isEmpty()) {
            RelocationStockSeeder.receiveFromSupplier(apiExecutor, UserRole.ADMIN, storageId, inputs);
        }
        return productionFixture.createAs(
                UserRole.ADMIN, storageId, techMap, amount, "nr-" + System.currentTimeMillis());
    }

    /**
     * Canonical IT dataset: plan 100, produced 30, intermediate stock 40, raw stock 50.
     */
    @Step("Канонічний набір: план 100, вироблено 30, залишки intermediate=40 raw=50")
    public ManufacturingItemResponse seedCanonicalPlanProductionAndStock(Long storageId, Chain chain) {
        createCurrentMonthPlan(storageId, chain.getProduct().getId(), PLAN_GOAL);
        return seedCanonicalProductionAndStock(storageId, chain);
    }

    @Step("Канонічне виробництво 30 і залишки 40/50 на складі {storageId} (план уже існує)")
    public ManufacturingItemResponse seedCanonicalProductionAndStock(Long storageId, Chain chain) {
        ManufacturingItemResponse production = produce(storageId, chain.getProductMap(), PRODUCED);
        setExactStock(storageId, chain.getIntermediate().getId(), INTERMEDIATE_STOCK);
        setExactStock(storageId, chain.getRaw().getId(), RAW_STOCK);
        return production;
    }

    @Step("POST needed-resources storageId={storageId}")
    public Response requestNeededRaw(UserRole role, Long storageId, ExecutionFilterRequest filter) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STATISTIC_POST_NEEDED_RESOURCES,
                role,
                filter,
                String.valueOf(storageId));
    }

    @Step("POST needed-resources (очікується 200) storageId={storageId}")
    public PlanNeededResourcesResponse requestNeeded(UserRole role, Long storageId, ExecutionFilterRequest filter) {
        Response response = requestNeededRaw(role, storageId, filter);
        validateSuccess(response, "POST needed-resources as " + role);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STATISTIC_POST_NEEDED_RESOURCES);
        return response.as(PlanNeededResourcesResponse.class);
    }

    public ExecutionFilterRequest currentMonth() {
        YearMonth now = YearMonth.now();
        return ExecutionFilterRequest.builder()
                .month(now.getMonthValue())
                .year(now.getYear())
                .build();
    }

    public NeededResourceResponse requireRow(PlanNeededResourcesResponse body, String resourceName) {
        return body.getNeededResources().stream()
                .filter(row -> row.getResource() != null && resourceName.equals(row.getResource().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing needed resource: " + resourceName
                        + "; present=" + body.getNeededResources().stream()
                        .map(r -> r.getResource() != null ? r.getResource().getName() : "?")
                        .toList()));
    }

    public boolean hasRow(PlanNeededResourcesResponse body, String resourceName) {
        return body.getNeededResources().stream()
                .anyMatch(row -> row.getResource() != null && resourceName.equals(row.getResource().getName()));
    }

    public List<ResourceCategoryResponse> listCategories() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        return DatabaseIntegrityValidator.extractList(response, ResourceCategoryResponse.class);
    }

    public TechnologicalMapResponse createAltGroupMap(Long storageId, double defaultAmount, double otherAmount) {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapRequest.builder()
                .name("NR-alt-" + System.currentTimeMillis())
                .type(TechnologicalMapDataFactory.TYPE_PRODUCTION)
                .input(List.of(new ResourceUsageRequest(resources.get(0).getId(), 1.0)))
                .output(List.of(new ResourceUsageRequest(resources.get(3).getId(), 1.0)))
                .storageIds(Set.of(storageId))
                .groups(List.of(TechnologicalMapDataFactory.alternativeGroup(
                        "NR-alts",
                        TechnologicalMapDataFactory.alternativeResource(resources.get(1).getId(), defaultAmount, true),
                        TechnologicalMapDataFactory.alternativeResource(resources.get(2).getId(), otherAmount, false))))
                .build();
        return techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
    }

    @Step("Деактивувати техкарту {techMap.id} на складі {storageId}")
    public void cleanupTechMap(TechnologicalMapResponse techMap, Long storageId) {
        if (techMap == null || techMap.getId() == null || storageId == null) {
            return;
        }
        techMapFixture.deactivateTechMap(UserRole.ADMIN, techMap.getId(), storageId);
    }

    @Value
    @Builder
    public static class Chain {
        ResourceResponse product;
        ResourceResponse intermediate;
        ResourceResponse raw;
        TechnologicalMapResponse productMap;
        TechnologicalMapResponse intermediateMap;
    }
}
