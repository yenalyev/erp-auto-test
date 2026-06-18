package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.UserRole;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ProductionFixture extends BaseFixture {

    private static final double INPUT_STOCK = 100.0;

    private final TechnologicalMapFixture techMapFixture;

    public ProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів виробництва")
    public void prepareContext() {
        if (testContext.get(ContextKey.PRODUCTION_TECH_MAP) != null) {
            return;
        }

        techMapFixture.prepareContext();

        Long storageId = ConfigProvider.getOwner1StorageId();
        TechnologicalMapResponse techMap = techMapFixture.createTechMapAs(UserRole.ADMIN, storageId);

        testContext.set(ContextKey.PRODUCTION_TECH_MAP, techMap);
        testContext.set(ContextKey.DYNAMIC_TECH_MAP, techMap);
        testContext.set(ContextKey.DYNAMIC_TECH_MAP_ID, techMap.getId());

        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        Long input1 = resources.get(0).getId();
        Long input2 = resources.get(1).getId();
        Long output = resources.get(2).getId();
        testContext.set(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS, List.of(input1, input2));
        testContext.set(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID, output);

        seedStockViaRelocation(storageId, input1, input2);
        log.info("Production fixture ready: techMap={}, storage={}, inputs=[{}, {}], outputResource={}",
                techMap.getId(), storageId, input1, input2, output);
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
        var request = com.erp.data.factories.production.ProductionDataFactory
                .buildCreateRequest(techMap, amount, java.time.LocalDate.now(), batchNumber);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_PUT_UPDATE,
                role,
                request,
                productionId,
                storageId);
        validateSuccess(response, "Update production id=" + productionId);
        return response.as(ManufacturingItemResponse.class);
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

    @Step("API: Отримати залишок ресурсу {resourceId} на складі {storageId}")
    public double getResourceStock(Long storageId, Long resourceId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                response, StorageItemResponse.class);
        if (items == null) {
            return 0.0;
        }
        return items.stream()
                .filter(i -> i.getResource() != null && resourceId.equals(i.getResource().getId()))
                .map(i -> i.getAmount() != null ? i.getAmount() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    private void topUpIfNeeded(Long storageId, Long resourceId, double minimum) {
        double current = getResourceStock(storageId, resourceId);
        if (current >= minimum) {
            return;
        }
        double toAdd = minimum - current;
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(resourceId, toAdd));
        log.info("Topped up resource {} on storage {}: {} → {}", resourceId, storageId, current, minimum);
    }

    @Step("FIXTURE: Seed input stock via relocation receive (SUPPLIER → storage {storageId})")
    private void seedStockViaRelocation(Long storageId, Long input1, Long input2) {
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(input1, INPUT_STOCK, input2, INPUT_STOCK));
        log.info("Seeded stock via relocation receive: storage={}, resources=[{}, {}], amount={}",
                storageId, input1, input2, INPUT_STOCK);
    }
}
