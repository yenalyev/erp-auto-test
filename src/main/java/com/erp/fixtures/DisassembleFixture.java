package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.disassemble.DisassembleDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.DisassembleListRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DisassembleItemResponse;
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

    public Long inputResourceId() {
        return testContext.get(ContextKey.DISASSEMBLE_INPUT_RESOURCE_ID);
    }

    public Long outputResourceId() {
        return testContext.get(ContextKey.DISASSEMBLE_OUTPUT_RESOURCE_ID);
    }

    public String inputResourceName() {
        return techMap().getInput().getFirst().getResource().getName().trim();
    }

    public String outputResourceName() {
        return techMap().getOutput().getFirst().getResource().getName().trim();
    }

    @Step("API: створити техкарту розбору для локації {storageId}")
    public TechnologicalMapResponse createDisassembleTechMapAs(UserRole role, Long storageId) {
        return createDisassembleTechMapAs(
                role, storageId, testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES));
    }

    @Step("API: створити техкарту розбору на {storageId} з явними input/output")
    public TechnologicalMapResponse createDisassembleTechMapAs(
            UserRole role, Long storageId, java.util.List<ResourceResponse> resources) {
        techMapFixture.setMode(storageId, com.erp.enums.StorageTechnologicalMapMode.EDIT_ALLOWED);

        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createDisassembleTechMap(resources, storageId)
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

    @Step("API: GET disassemble {id} on storage {storageId}")
    public DisassembleItemResponse getById(UserRole role, long id, long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.DISASSEMBLE_GET_BY_ID,
                role,
                null,
                id,
                storageId);
        validateSuccess(response, "Get disassemble");
        return response.as(DisassembleItemResponse.class);
    }

    @Step("API: PUT disassemble {id} on storage {storageId}")
    public Response updateRaw(UserRole role,
                              long id,
                              long storageId,
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
        return apiExecutor.execute(
                ApiEndpointDefinition.DISASSEMBLE_PUT_UPDATE,
                role,
                request,
                id,
                storageId);
    }

    @Step("API: DELETE disassemble {id}")
    public Response deleteRaw(UserRole role, long id, long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.DISASSEMBLE_DELETE,
                role,
                null,
                id,
                storageId);
    }
    public double getProducedSummaryAmount(long storageId, UserRole role, long resourceId) {
        Response history = getOperationHistoryToday(storageId, role);
        validateSuccess(history, "Get operation history for produced summary");
        return extractSummaryAmountForResource(history, "totalProducedResources", resourceId);
    }

    @Step("API: сумарна «Використано» для ресурсу {resourceId} за сьогодні")
    public double getUsedSummaryAmount(long storageId, UserRole role, long resourceId) {
        Response history = getOperationHistoryToday(storageId, role);
        validateSuccess(history, "Get operation history for used summary");
        return extractSummaryAmountForResource(history, "totalUsedResources", resourceId);
    }

    @Step("API: сума розібраного input {resourceId} за сьогодні (план-execution «Розбір»)")
    public double getTodayDisassembledAmount(long storageId, UserRole role, long resourceId) {
        Response page = getDisassemblePageForCurrentMonth(storageId, role);
        validateSuccess(page, "Get disassemble page for today disassembled amount");
        return sumTodayFieldForInput(page, resourceId, "amount");
    }

    @Step("API: сума output totalAmount {resourceId} за сьогодні (план-execution «Отримано»)")
    public double getTodayDisassembleOutputAmount(long storageId, UserRole role, long resourceId) {
        Response page = getDisassemblePageForCurrentMonth(storageId, role);
        validateSuccess(page, "Get disassemble page for today output amount");
        return sumTodayOutputTotalAmount(page, resourceId);
    }

    @Step("API: GET /disassemble page за поточний місяць для складу {storageId}")
    public Response getDisassemblePageForCurrentMonth(long storageId, UserRole role) {
        LocalDate today = LocalDate.now();
        Map<String, Object> params = new HashMap<>();
        params.put("storageIds", storageId);
        params.put("startDate", today.withDayOfMonth(1).toString());
        params.put("endDate", today.toString());
        params.put("page", 0);
        params.put("size", 9999);
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DISASSEMBLE_GET_PAGE,
                role,
                params);
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

    private double extractSummaryAmountForResource(Response history, String arrayPath, long resourceId) {
        List<Map<String, Object>> rows = history.jsonPath().getList(arrayPath);
        if (rows == null || rows.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < rows.size(); i++) {
            Long id = history.jsonPath().getLong(arrayPath + "[" + i + "].resource.id");
            if (id != null && id == resourceId) {
                Number amount = history.jsonPath().get(arrayPath + "[" + i + "].amount");
                return amount != null ? amount.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }

    private double sumTodayFieldForInput(Response page, long inputResourceId, String field) {
        String today = LocalDate.now().toString();
        List<Map<String, Object>> content = page.jsonPath().getList("content");
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < content.size(); i++) {
            String date = page.jsonPath().getString("content[" + i + "].date");
            Long id = page.jsonPath().getLong("content[" + i + "].itemForDisassemble.id");
            if (today.equals(date) && id != null && id == inputResourceId) {
                Number amount = page.jsonPath().get("content[" + i + "]." + field);
                if (amount != null) {
                    sum += amount.doubleValue();
                }
            }
        }
        return sum;
    }

    private double sumTodayOutputTotalAmount(Response page, long outputResourceId) {
        String today = LocalDate.now().toString();
        List<Map<String, Object>> content = page.jsonPath().getList("content");
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < content.size(); i++) {
            String date = page.jsonPath().getString("content[" + i + "].date");
            if (!today.equals(date)) {
                continue;
            }
            List<Map<String, Object>> outputs = page.jsonPath().getList("content[" + i + "].outputs");
            if (outputs == null) {
                continue;
            }
            for (int j = 0; j < outputs.size(); j++) {
                Long id = page.jsonPath().getLong("content[" + i + "].outputs[" + j + "].resource.id");
                if (id != null && id == outputResourceId) {
                    Number amount = page.jsonPath().get("content[" + i + "].outputs[" + j + "].totalAmount");
                    if (amount != null) {
                        sum += amount.doubleValue();
                    }
                }
            }
        }
        return sum;
    }

    @Step("FIXTURE: Seed input stock for disassemble via relocation receive")
    public void seedInputStock(Long storageId, Long inputResourceId) {
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(inputResourceId, INPUT_STOCK));
        log.info("Seeded disassemble input stock: storage={}, resource={}, amount={}",
                storageId, inputResourceId, INPUT_STOCK);
    }
}
