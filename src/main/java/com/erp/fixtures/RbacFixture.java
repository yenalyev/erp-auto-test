package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.measurement_unit.MeasurementUnitRequestDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.MeasurementUnitRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ProductionResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;

import java.util.ArrayList;
import java.util.List;

import static com.erp.test_context.ContextKey.OWNER_1_STORAGE_ID;

@Slf4j
public class RbacFixture extends BaseFixture {

    public RbacFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Публічний API для тестів. Створює все необхідне одним викликом.
     */
    @Step("Setup complete ERP test data context")
    public void prepareFullRbacContext() {
        log.info("Starting ERP test data generation...");
        fetchSharedUnit();
        setupSharedResource();
        setupSharedResourceList(4);
        prepareTechMapForUpdate();
        setupDynamicProductionList(3, UserRole.OWNER_1);
    }

    @Step("Ensure realistic Measurement Units exist (min 5)")
    public void fetchSharedUnit() {
        ApiEndpointDefinition getEndpoint = ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL;

        // 1. Отримуємо те, що вже є
        Response response = apiExecutor.execute(getEndpoint, UserRole.ADMIN);
        List<MeasurementUnitResponse> existing = response.jsonPath().getList("", MeasurementUnitResponse.class);
        if (existing == null) existing = new ArrayList<>();

        // 2. Запитуємо у фабрики список того, чого не вистачає до 5 одиниць
        List<MeasurementUnitRequest> toCreate = MeasurementUnitRequestDataFactory.getMissingUnits(existing, 5);

        if (!toCreate.isEmpty()) {
            log.info("📊 Database needs {} more units. Starting creation...", toCreate.size());

            for (MeasurementUnitRequest request : toCreate) {
                Response createRes = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, UserRole.ADMIN, request);
                validateSuccess(createRes, "Create MU: " + request.getName());
            }

            // 3. Оновлюємо фінальний список після створення
            response = apiExecutor.execute(getEndpoint, UserRole.ADMIN);
            existing = response.jsonPath().getList("", MeasurementUnitResponse.class);
        }

        // 4. Зберігаємо в контекст
        testContext.set(ContextKey.SHARED_UNIT_ID, existing.get(0).getId());
        testContext.set(ContextKey.SHARED_MEASUREMENT_UNIT_LIST, existing);

        log.info("✅ Measurement units ready. Total: {}", existing.size());
    }

    @Step("Create Shared Resource")
    public void setupSharedResource() {
        ApiEndpointDefinition endpoint = ApiEndpointDefinition.RESOURCE_CREATE;

        // Генеруємо тіло запиту. RequestBodyFactory візьме SHARED_UNIT_ID з контексту
        Object body = RequestBodyFactory.generate(endpoint, testContext);

        Response response = apiExecutor.execute(endpoint, UserRole.ADMIN, body);
        validateSuccess(response,  "Create Resource");

        ResourceResponse resource = response.as((Class<ResourceResponse>) endpoint.getResponseClass());

        // Зберігаємо і ID, і повний об'єкт (для апдейтів у фабриці)
        testContext.set(ContextKey.SHARED_RESOURCE, resource);
        testContext.set(ContextKey.SHARED_RESOURCE_ID, resource.getId());
    }

    @Step("Ensure there are at least {length} shared resources")
    public void setupSharedResourceList(@NonNegative int length) {
        ApiEndpointDefinition getEndpoint = ApiEndpointDefinition.RESOURCE_GET_ALL;
        Response response = apiExecutor.execute(getEndpoint, UserRole.ADMIN);

        // Гарантуємо, що список можна змінювати (ArrayList)
        List<ResourceResponse> allResources = new ArrayList<>(
                response.jsonPath().getList("", ResourceResponse.class)
        );

        if (allResources.size() < length) {
            int currentSize = allResources.size();
            int needed = length - currentSize;
            log.info("Current resources: {}. Creating {} more to reach {}", currentSize, needed, length);

            for (int i = 0; i < needed; i++) {
                ApiEndpointDefinition createEndpoint = ApiEndpointDefinition.RESOURCE_CREATE;
                Object body = RequestBodyFactory.generate(createEndpoint, testContext);

                Response createResponse = apiExecutor.execute(createEndpoint, UserRole.ADMIN, body);
                validateSuccess(createResponse, "Create Resource during setup");

                ResourceResponse createdResource = createResponse.as(ResourceResponse.class);
                allResources.add(createdResource);
            }
        }

        testContext.set(ContextKey.SHARED_AVAILABLE_RESOURCES, allResources);
    }

    @Step("Ensure there are at least {length} productions resources")
    public void setupDynamicProductionList(@NonNegative int length,
                                           @NonNull UserRole userRole) {
        ApiEndpointDefinition getEndpoint =
                ApiEndpointDefinition.PRODUCTION_GET_ALL_BY_STORE_ID;
        Response response = apiExecutor.execute(getEndpoint, userRole, userRole.getStoreId());

        // Гарантуємо, що список можна змінювати (ArrayList)
        List<ProductionResponse> allProductions = new ArrayList<>(
                response.jsonPath().getList("", ProductionResponse.class)
        );

        if (allProductions.size() < length) {
            int currentSize = allProductions.size();
            int needed = length - currentSize;
            log.info("Current allProductionList: {}. Creating {} more to reach {}", currentSize, needed, length);

            //ensure that we have at least one techMap
            TechnologicalMapResponse techMap = testContext.get(ContextKey.DYNAMIC_TECH_MAP);
            if (null == techMap){
                prepareTechMapForUpdate();
            }

            TechnologicalMapResponse techMapGuaranteed = testContext.get(ContextKey.DYNAMIC_TECH_MAP);

            for (int i = 0; i < needed; i++) {
                //Create productions
                Object body = ProductionDataFactory
                        .simpleProduction(Long.valueOf(userRole.getStoreId()),
                                techMapGuaranteed, FakerProvider.price(1D,100D));

                ApiEndpointDefinition createEndpoint =
                        ApiEndpointDefinition.PRODUCTION_POST_CREATE_BY_OWNER_1_STORE_ID;

                Response createResponse = apiExecutor.execute(createEndpoint, userRole, body, userRole.getStoreId());
                validateSuccess(createResponse, "Create Production during setup");

                ProductionResponse createdRProduction = createResponse.as(ProductionResponse.class);
                allProductions.add(createdRProduction);
            }
        }

        testContext.set(ContextKey.DYNAMIC_PRODUCTIONS, allProductions);
    }

    @Step("Prepare dynamic Tech Map for update test")
    public void prepareTechMapForUpdate() {
        List<ResourceResponse> sharedResources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);

        // Створюємо техкарту через API
        TechnologicalMapRequest createRequest = TechnologicalMapDataFactory.createSimpleTechMap(sharedResources).build();
        Response response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, createRequest);

        TechnologicalMapResponse createdMap = response.as(TechnologicalMapResponse.class);

        // Кладемо в контекст і об'єкт, і ID (для URL)
        testContext.set(ContextKey.DYNAMIC_TECH_MAP, createdMap);
        testContext.set(ContextKey.DYNAMIC_TECH_MAP_ID, createdMap.getId());
    }

    private void validateSuccess(Response response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("ERROR {} failed! Status: {}. Body: {}", action,
                    response.statusCode(), response.body().asString());
            throw new RuntimeException("Fixture setup critical failure: " + action);
        }
        log.info("✅ {} successful (Status: {})", action, response.statusCode());
    }
}