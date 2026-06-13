package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageTechnologicalMapModeRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageTechnologicalMapModeResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TechnologicalMapFixture extends BaseFixture {

    private static final int REQUIRED_RESOURCES = 3;
    private static final int RESOURCE_PAGE_SIZE = 10;

    private final ResourceFixture resourceFixture;

    public TechnologicalMapFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів техкарт")
    public void prepareContext() {
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();
        ensureTechMapResources(REQUIRED_RESOURCES);
    }

    public Long getOwner1StorageId() {
        return ConfigProvider.getOwner1StorageId();
    }

    /**
     * Без GET /resources?size=9999 — беремо невелику сторінку або створюємо нестачу.
     */
    @Step("Ensure at least {count} resources for tech map tests")
    public void ensureTechMapResources(int count) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_GET_PAGE,
                UserRole.ADMIN,
                String.valueOf(Math.max(count, RESOURCE_PAGE_SIZE)));

        List<ResourceResponse> resources = new ArrayList<>(
                DatabaseIntegrityValidator.extractList(response, ResourceResponse.class));

        while (resources.size() < count) {
            Object body = RequestBodyFactory.generate(ApiEndpointDefinition.RESOURCE_CREATE, testContext);
            Response createResponse = apiExecutor.execute(
                    ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, body);
            validateSuccess(createResponse, "Create resource for tech map setup");
            resources.add(createResponse.as(ResourceResponse.class));
        }

        List<ResourceResponse> selected = resources.subList(0, count);
        testContext.set(ContextKey.SHARED_RESOURCE_ID, selected.getFirst().getId());
        testContext.set(ContextKey.SHARED_RESOURCE, selected.getFirst());
        testContext.set(ContextKey.SHARED_AVAILABLE_RESOURCES, new ArrayList<>(selected));
        log.info("Tech map resources ready: {}", selected.size());
    }

    @Step("ADMIN: встановити режим редагування техкарт для локації {storageId} → {mode}")
    public StorageTechnologicalMapModeResponse setMode(
            Long storageId,
            StorageTechnologicalMapMode mode) {
        StorageTechnologicalMapModeRequest request = StorageTechnologicalMapModeRequest.builder()
                .storageId(storageId)
                .mode(mode)
                .build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_MODE_UPDATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Update tech map mode for storage " + storageId);

        return response.as(StorageTechnologicalMapModeResponse.class);
    }

    @Step("Перевірити режим редагування техкарт для локації {storageId}")
    public void assertMode(Long storageId, UserRole role, StorageTechnologicalMapMode expectedMode) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_MODE_GET,
                role,
                String.valueOf(storageId));
        validateSuccess(response, "Get tech map mode for storage " + storageId);

        StorageTechnologicalMapModeResponse modeResponse = response.as(StorageTechnologicalMapModeResponse.class);
        assertThat(modeResponse.getStorageId()).isEqualTo(storageId);
        assertThat(modeResponse.getMode()).isEqualTo(expectedMode);
    }

    @Step("GET tech maps for storage {storageId} by name")
    public List<TechnologicalMapResponse> getTechMapsByName(Long storageId, UserRole role, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_BY_STORAGE_AND_NAME,
                role,
                null,
                String.valueOf(storageId),
                name);
        validateSuccess(response, "Get tech maps for storage " + storageId + " name=" + name);
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    public long countTechMapsByName(Long storageId, UserRole role, String name) {
        return getTechMapsByName(storageId, role, name).size();
    }

    @Step("GET active tech maps for storage {storageId} by name")
    public List<TechnologicalMapResponse> getActiveTechMapsByName(Long storageId, UserRole role, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_ACTIVE_BY_STORAGE_AND_NAME,
                role,
                null,
                String.valueOf(storageId),
                name);
        validateSuccess(response, "Get active tech maps for storage " + storageId + " name=" + name);
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    public long countActiveTechMapsByName(Long storageId, UserRole role, String name) {
        return getActiveTechMapsByName(storageId, role, name).size();
    }

    @Step("Створити техкарту для локації {storageId} (режим EDIT_ALLOWED)")
    public TechnologicalMapResponse createTechMapAs(UserRole role, Long storageId) {
        setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        TechnologicalMapRequest request = buildOwner1CreateRequest();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_CREATE,
                role,
                request);
        validateSuccess(response, "Create tech map for storage " + storageId);

        return response.as(TechnologicalMapResponse.class);
    }

    @Step("Перевірити відмову через закритий режим редагування для локації {storageId}")
    public void assertEditForbidden(Response response, Long storageId) {
        assertThat(response.statusCode()).isEqualTo(400);
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про заборону редагування")
                .contains("закрито")
                .contains(String.valueOf(storageId));
    }

    @Step("Побудувати запит на створення техкарти для локації Owner1")
    public TechnologicalMapRequest buildOwner1CreateRequest() {
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        if (resources == null || resources.size() < REQUIRED_RESOURCES) {
            throw new IllegalStateException("SHARED_AVAILABLE_RESOURCES missing or too small");
        }
        return TechnologicalMapDataFactory
                .createProductionTechMap(resources, getOwner1StorageId())
                .build();
    }
}
