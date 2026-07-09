package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.ResourceDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.ResourcePriceResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ResourceFixture extends BaseFixture {

    public ResourceFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Create test data for Measurement Unit functional tests
     */
    @Step("Setup Measurement Unit functional test data context")
    public void prepareContext() {
        log.info("Starting Measurement Unit functional test data generation...");
        fetchSharedUnit(5);
        fetchSharedResourceCategory();
        setupSharedResourceList(5);
    }

    @Step("API: створити ізольований ресурс «{namePrefix}»")
    public ResourceResponse createUniqueResource(String namePrefix) {
        Long categoryId = testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        return createUniqueResource(namePrefix, categoryId);
    }

    @Step("API: створити ізольований ресурс «{namePrefix}» у категорії {categoryId}")
    public ResourceResponse createUniqueResource(String namePrefix, Long categoryId) {
        Long unitId = testContext.get(com.erp.test_context.ContextKey.SHARED_UNIT_ID);
        ResourceRequest request = ResourceDataFactory.uniqueResource(namePrefix, unitId, categoryId);

        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create isolated resource " + namePrefix);
        return response.as(ResourceResponse.class);
    }

    @Step("API: деактивувати ресурс id={resourceId}")
    public Response deactivate(UserRole role, Long resourceId) {
        return apiExecutor.execute(ApiEndpointDefinition.RESOURCE_DEACTIVATE, role, null, String.valueOf(resourceId));
    }

    @Step("API: реактивувати ресурс id={resourceId}")
    public Response unarchive(UserRole role, Long resourceId) {
        return apiExecutor.execute(ApiEndpointDefinition.RESOURCE_UNARCHIVE, role, null, String.valueOf(resourceId));
    }

    @Step("API: GET ресурс id={resourceId}")
    public ResourceResponse getById(UserRole role, Long resourceId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_GET_BY_ID, role, null, String.valueOf(resourceId));
        validateSuccess(response, "Get resource by id " + resourceId);
        return response.as(ResourceResponse.class);
    }

    @Step("API: GET сторінка ресурсів isActive={isActive}")
    public List<ResourceResponse> getPage(UserRole role, Boolean isActive, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", 500);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        if (name != null && !name.isBlank()) {
            params.put("name", name);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_GET_ALL, role, params);
        validateSuccess(response, "Get resources page isActive=" + isActive);
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    @Step("API: autocomplete search='{search}' storageId={storageId}")
    public List<ResourceResponse> autocompleteForStorage(
            UserRole role, Long storageId, String search, boolean includeArchived) {
        Map<String, Object> params = new HashMap<>();
        params.put("storageId", storageId);
        params.put("search", search);
        params.put("size", 50);
        if (includeArchived) {
            params.put("includeArchived", true);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_AUTOCOMPLETE, role, params);
        validateSuccess(response, "Autocomplete resources storageId=" + storageId);
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    @Step("API: GET сторінка ресурсів storageId={storageId}")
    public List<ResourceResponse> getPageForStorage(UserRole role, Long storageId, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", 500);
        params.put("storageId", storageId);
        if (name != null && !name.isBlank()) {
            params.put("name", name);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_GET_ALL, role, params);
        validateSuccess(response, "Get resources page storageId=" + storageId);
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    public boolean isPresentInAutocompleteForStorage(
            UserRole role, Long storageId, String search, Long resourceId, boolean includeArchived) {
        return autocompleteForStorage(role, storageId, search, includeArchived).stream()
                .anyMatch(r -> Objects.equals(r.getId(), resourceId));
    }

    @Step("API: autocomplete search='{search}'")
    public List<ResourceResponse> autocomplete(UserRole role, String search, boolean includeArchived) {
        return autocomplete(role, search, includeArchived, null);
    }

    @Step("API: autocomplete search='{search}' categoryIds={categoryIds}")
    public List<ResourceResponse> autocomplete(
            UserRole role, String search, boolean includeArchived, List<Long> categoryIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("size", 50);
        if (includeArchived) {
            params.put("includeArchived", true);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            params.put("categoryIds", categoryIds);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_AUTOCOMPLETE, role, params);
        validateSuccess(response, "Autocomplete resources search=" + search);
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    /**
     * Catalog for Plan Execution «Керувати обраними»:
     * {@code GET /resources/with-technological-map?storageId=&name=&isActive=}.
     * UI selector «Активні» → {@code isActive=true}; «Архівні» → {@code isActive=false}.
     */
    @Step("API: GET /resources/with-technological-map storageId={storageId} isActive={isActive} name={name}")
    public List<ResourceResponse> getWithTechnologicalMap(
            UserRole role, Long storageId, Boolean isActive, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("storageId", storageId);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        if (name != null && !name.isBlank()) {
            params.put("name", name);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_WITH_TECHNOLOGICAL_MAP, role, params);
        validateSuccess(response, "GET with-technological-map storageId=" + storageId
                + " isActive=" + isActive);
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    @Step("API: GET ціни ресурсів isActive={isActive}")
    public List<ResourcePriceResponse> getResourcePrices(UserRole role, Boolean isActive, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", 500);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        if (name != null && !name.isBlank()) {
            params.put("name", name);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_PRICE_GET_PAGE, role, params);
        validateSuccess(response, "Get resource prices isActive=" + isActive);
        return DatabaseIntegrityValidator.extractList(response, ResourcePriceResponse.class);
    }

    @Step("API: створити сповіщення про залишки для ресурсу {resourceId} на складі {storageId}")
    public void createStockAlert(UserRole role, Long storageId, Long resourceId, double limit) {
        new AlertFixture(testContext, apiExecutor)
                .createOrUpdateStockAlert(role, storageId, resourceId, limit);
    }

    public boolean isPresentInActiveDictionary(UserRole role, Long resourceId) {
        return isPresentInActiveDictionary(role, resourceId, null);
    }

    public boolean isPresentInActiveDictionary(UserRole role, Long resourceId, String nameFilter) {
        return getPage(role, true, nameFilter).stream()
                .anyMatch(r -> Objects.equals(r.getId(), resourceId));
    }

    public boolean isPresentInDeactivatedPage(UserRole role, Long resourceId) {
        return isPresentInDeactivatedPage(role, resourceId, null);
    }

    public boolean isPresentInDeactivatedPage(UserRole role, Long resourceId, String nameFilter) {
        return getPage(role, false, nameFilter).stream()
                .anyMatch(r -> Objects.equals(r.getId(), resourceId));
    }

    public boolean isPresentInAutocomplete(UserRole role, String search, Long resourceId, boolean includeArchived) {
        return autocomplete(role, search, includeArchived).stream()
                .anyMatch(r -> Objects.equals(r.getId(), resourceId));
    }

    public boolean isPresentInResourcePrices(UserRole role, Long resourceId) {
        return getResourcePrices(role, true, null).stream()
                .anyMatch(p -> Objects.equals(p.getResourceId(), resourceId));
    }

    public static void assertDeactivationRejected(Response response, String expectedFragment) {
        assertThat(response.statusCode())
                .as("Деактивація мала бути відхилена з 400")
                .isEqualTo(400);
        assertAnyErrorMessageContains(response, expectedFragment);
    }

    public static void assertAnyErrorMessageContains(Response response, String expectedFragment) {
        List<String> messages = response.jsonPath().getList("errors[0].messages");
        if (messages != null && !messages.isEmpty()) {
            assertThat(messages.stream().anyMatch(m -> m != null && m.contains(expectedFragment)))
                    .as("Хоча б одне повідомлення має містити «%s», отримано: %s", expectedFragment, messages)
                    .isTrue();
            return;
        }
        String single = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(single)
                .as("Повідомлення про помилку має містити «%s»", expectedFragment)
                .isNotBlank()
                .contains(expectedFragment);
    }

    public static void assertResourceStillActive(ResourceResponse resource) {
        assertThat(resource.getActive())
                .as("Ресурс id=%d має залишитись активним", resource.getId())
                .isNotEqualTo(Boolean.FALSE);
    }
}
