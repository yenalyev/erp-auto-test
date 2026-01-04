package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.enums.UserRole;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

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

    private void validateSuccess(Response response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("ERROR {} failed! Status: {}. Body: {}", action,
                    response.statusCode(), response.body().asString());
            throw new RuntimeException("Fixture setup critical failure: " + action);
        }
        log.info("✅ {} successful (Status: {})", action, response.statusCode());
    }
}