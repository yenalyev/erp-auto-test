package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.ResourceBundleResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ApiResponseHelper;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class ResourceBundleFixture extends BaseFixture {

    private final RelocationFixture relocationFixture;
    private final List<TrackedBundle> createdBundles = new CopyOnWriteArrayList<>();

    public ResourceBundleFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.relocationFixture = new RelocationFixture(testContext, apiExecutor);
    }

    public RelocationFixture relocation() {
        return relocationFixture;
    }

    @Step("FIXTURE: Підготовка середовища для комплектів видачі")
    public void prepareContext() {
        relocationFixture.prepareContext();
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        if (resources == null || resources.size() < 2) {
            throw new IllegalStateException("Need at least 2 shared resources for bundle tests");
        }
        Long owner1 = ConfigProvider.getOwner1StorageId();
        for (ResourceResponse resource : resources.subList(0, Math.min(3, resources.size()))) {
            relocationFixture.ensureStock(owner1, resource.getId(), 50.0);
        }
    }

    public String uniqueBundleName(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    @Step("API: GET user-bundles storageId={storageId}")
    public Response getBundlesRaw(UserRole role, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_USER_BUNDLES_GET,
                role,
                null,
                storageId);
    }

    @Step("API: list user-bundles storageId={storageId}")
    public List<ResourceBundleResponse> listBundles(UserRole role, Long storageId) {
        Response response = getBundlesRaw(role, storageId);
        if (response.statusCode() >= 400) {
            return List.of();
        }
        List<ResourceBundleResponse> list = ApiResponseHelper.parseList(
                response, ResourceBundleResponse.class, "GET user-bundles");
        return list != null ? list : List.of();
    }

    @Step("API: save user-bundle name={bundleName}")
    public Response saveBundleRaw(UserRole role, Long storageId, String bundleName, List<Long> resourceIds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageId", storageId);
        params.put("bundleName", bundleName);
        params.put("resources", resourceIds);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_USER_BUNDLES_POST,
                role,
                params);
        if (response.statusCode() < 300) {
            createdBundles.add(new TrackedBundle(storageId, bundleName, role));
        }
        return response;
    }

    @Step("API: create user-bundle name={bundleName}")
    public ResourceBundleResponse createBundle(UserRole role, Long storageId, String bundleName, List<Long> resourceIds) {
        Response response = saveBundleRaw(role, storageId, bundleName, resourceIds);
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Failed to save bundle '" + bundleName + "': HTTP " + response.statusCode()
                            + " body=" + response.asString());
        }
        return listBundles(role, storageId).stream()
                .filter(b -> bundleName.equals(b.getBundleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Bundle '" + bundleName + "' not found after save"));
    }

    @Step("API: DELETE user-bundle name={bundleName}")
    public Response deleteBundleRaw(UserRole role, Long storageId, String bundleName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageId", storageId);
        params.put("bundleName", bundleName);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_USER_BUNDLES_DELETE,
                role,
                params);
        createdBundles.removeIf(b -> b.storageId().equals(storageId) && b.bundleName().equals(bundleName));
        return response;
    }

    @Step("API: cleanup created user-bundles")
    public void cleanupCreatedBundles() {
        List<TrackedBundle> snapshot = new ArrayList<>(createdBundles);
        for (TrackedBundle tracked : snapshot) {
            try {
                deleteBundleRaw(tracked.role(), tracked.storageId(), tracked.bundleName());
            } catch (Exception e) {
                log.warn("Failed to cleanup bundle {} on {}: {}",
                        tracked.bundleName(), tracked.storageId(), e.getMessage());
            }
        }
        createdBundles.clear();
    }

    public List<Long> sharedResourceIds(int count) {
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        return resources.stream().limit(count).map(ResourceResponse::getId).toList();
    }

    private record TrackedBundle(Long storageId, String bundleName, UserRole role) {
    }
}
