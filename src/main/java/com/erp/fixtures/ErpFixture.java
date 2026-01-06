package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ErpFixture extends BaseFixture {

    public ErpFixture(TestContext testContext, ApiExecutor apiExecutor) {
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
    }

    @Step("Fetch existing Measurement Unit")
    public void fetchSharedUnit() {
        ApiEndpointDefinition endpoint = ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL;
        Response response = apiExecutor.execute(endpoint, UserRole.ADMIN);

        validateSuccess(response, "Fetch Units");

        List<MeasurementUnitResponse> units = response.jsonPath()
                .getList("", (Class<MeasurementUnitResponse>) endpoint.getResponseElementType());

        if (units == null || units.isEmpty()) {
            throw new IllegalStateException("Database must have at least one Measurement Unit");
        }

        testContext.set(ContextKey.SHARED_UNIT_ID, units.get(0).getId());
        testContext.set(ContextKey.SHARED_MEASUREMENT_UNIT_LIST, units);
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