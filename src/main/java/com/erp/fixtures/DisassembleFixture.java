package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.disassemble.DisassembleDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.DisassembleListRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DisassembleFixture extends BaseFixture {

    private static final double INPUT_STOCK = 100.0;

    private final TechnologicalMapFixture techMapFixture;

    public DisassembleFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів розбору")
    public void prepareContext() {
        if (testContext.get(ContextKey.DISASSEMBLE_TECH_MAP) != null) {
            return;
        }

        techMapFixture.prepareContext();

        Long storageId = ConfigProvider.getOwner1StorageId();
        TechnologicalMapResponse techMap = createDisassembleTechMapAs(UserRole.ADMIN, storageId);

        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        Long inputResourceId = resources.get(0).getId();
        Long outputResourceId = resources.get(1).getId();

        testContext.set(ContextKey.DISASSEMBLE_TECH_MAP, techMap);
        testContext.set(ContextKey.DISASSEMBLE_INPUT_RESOURCE_ID, inputResourceId);
        testContext.set(ContextKey.DISASSEMBLE_OUTPUT_RESOURCE_ID, outputResourceId);

        seedInputStock(storageId, inputResourceId);
        log.info("Disassemble fixture ready: techMap={}, storage={}, input={}, output={}",
                techMap.getId(), storageId, inputResourceId, outputResourceId);
    }

    public TechnologicalMapResponse techMap() {
        return testContext.get(ContextKey.DISASSEMBLE_TECH_MAP);
    }

    public Long outputResourceId() {
        return testContext.get(ContextKey.DISASSEMBLE_OUTPUT_RESOURCE_ID);
    }

    @Step("API: створити техкарту розбору для локації {storageId}")
    public TechnologicalMapResponse createDisassembleTechMapAs(UserRole role, Long storageId) {
        techMapFixture.setMode(storageId, com.erp.enums.StorageTechnologicalMapMode.EDIT_ALLOWED);

        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createDisassembleTechMap(
                        testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES),
                        storageId)
                .build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_CREATE,
                role,
                request);
        validateSuccess(response, "Create disassemble tech map for storage " + storageId);
        return response.as(TechnologicalMapResponse.class);
    }

    @Step("API: створити розбір — {disassembleAmount} од., фактично отримано {actualTotalProduced} од.")
    public Response createAs(UserRole role,
                               Long storageId,
                               TechnologicalMapResponse techMap,
                               double disassembleAmount,
                               double actualTotalProduced,
                               String batchNumber) {
        DisassembleListRequest request = DisassembleDataFactory.buildCreateRequest(
                techMap,
                disassembleAmount,
                actualTotalProduced,
                LocalDate.now(),
                batchNumber);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.DISASSEMBLE_POST_CREATE,
                role,
                request,
                String.valueOf(storageId));
        validateSuccess(response, "Create disassemble batch=" + batchNumber);
        return response;
    }

    @Step("API: сумарна «Вироблено» для ресурсу {resourceId} за сьогодні")
    public double getProducedSummaryAmount(long storageId, UserRole role, long resourceId) {
        Response history = getOperationHistoryToday(storageId, role);
        validateSuccess(history, "Get operation history for produced summary");
        return extractProducedAmountForResource(history, resourceId);
    }

    @Step("API: Історія операцій за сьогодні для складу {storageId}")
    public Response getOperationHistoryToday(long storageId, UserRole role) {
        LocalDate today = LocalDate.now();
        Map<String, Object> params = new HashMap<>();
        params.put("storageIds", storageId);
        params.put("from", today.toString());
        params.put("to", today.toString());
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_OPERATION_HISTORY_GET,
                role,
                params);
    }

    private double extractProducedAmountForResource(Response history, long resourceId) {
        List<Map<String, Object>> produced = history.jsonPath().getList("totalProducedResources");
        if (produced == null || produced.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < produced.size(); i++) {
            Long id = history.jsonPath().getLong("totalProducedResources[" + i + "].resource.id");
            if (id != null && id == resourceId) {
                Number amount = history.jsonPath().get("totalProducedResources[" + i + "].amount");
                return amount != null ? amount.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }

    @Step("FIXTURE: Seed input stock for disassemble via relocation receive")
    private void seedInputStock(Long storageId, Long inputResourceId) {
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(inputResourceId, INPUT_STOCK));
        log.info("Seeded disassemble input stock: storage={}, resource={}, amount={}",
                storageId, inputResourceId, INPUT_STOCK);
    }
}
