package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises both the unrestricted JPA query and the restricted native resource query. */
public class ResourceModifiedSortFixture {
    private final ApiExecutor api;
    private final ResourceFixture resources;
    private final StorageFixture storages;
    private final StorageRegionFixture regions;
    private final List<ResourceResponse> owned = new ArrayList<>();
    private Long storageId;
    private Long regionId;
    private List<Row> ascending;
    public final String marker = "res-mod-sort-" + UUID.randomUUID().toString().substring(0, 8);

    public record Row(long id, String name, Instant updatedAt) { }

    public ResourceModifiedSortFixture(TestContext context, ApiExecutor api) {
        this.api = api;
        resources = new ResourceFixture(context, api);
        storages = new StorageFixture(context, api);
        regions = new StorageRegionFixture(context, api);
    }

    @Step("Підготувати три ресурси з різними датами та обмежену локацію")
    public void prepare() {
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        for (String suffix : List.of("C-", "B-", "A-")) {
            owned.add(resources.createUniqueResource(marker + "-" + suffix));
        }
        // Updating the oldest resource makes date order differ from both name and creation order.
        resources.updateNotes(UserRole.ADMIN, owned.getFirst().getId(), marker + " updated");
        List<Row> baseline = rows(list(null, null, marker, 0, 10).asString());
        assertThat(baseline).extracting(Row::id)
                .containsExactlyInAnyOrderElementsOf(owned.stream().map(ResourceResponse::getId).toList());
        ascending = baseline.stream().sorted(Comparator.comparing(Row::updatedAt)).toList();
        assertThat(ascending.stream().map(Row::updatedAt).distinct().count()).isEqualTo(3);
        assertThat(ascending).extracting(Row::id)
                .containsExactly(owned.get(1).getId(), owned.get(2).getId(), owned.get(0).getId());

        var parent = storages.resolveParentUnit();
        var storage = storages.createStorage(StorageDataFactory.restrictedUnitStorage(
                parent.getId(), marker + "-unit-").build());
        storageId = storage.getId();
        regionId = regions.createRegion(storage, StorageAccessMode.RESOURCES, marker + "-region-").getId();
        regions.addRegionMembers(regionId, storageId);
        regions.addRegionResources(regionId, owned.stream().map(ResourceResponse::getId).toArray(Long[]::new));
    }

    @Step("GET ресурси: storageId={scope}, sort={direction}, name={name}, page={page}, size={size}")
    public Response list(Long scope, String direction, String name, int page, int size) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("isActive", true);
        params.put("page", page);
        params.put("size", size);
        if (scope != null) params.put("storageId", scope);
        if (direction != null) params.put("sort", "updated_at," + direction);
        Response response = api.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_GET_ALL, UserRole.ADMIN, params);
        assertThat(response.statusCode()).as("Resource list must return HTTP 200, including updated_at sort")
                .isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getList("content")).isNotNull();
        return response;
    }

    public static List<Row> rows(String json) {
        List<Map<String, Object>> content = JsonPath.from(json).getList("content");
        assertThat(content).as("Paged resource content").isNotNull();
        return content.stream().map(row -> new Row(((Number) row.get("id")).longValue(),
                (String) row.get("name"), Instant.parse((String) row.get("updatedAt")))).toList();
    }

    public List<Row> expected(String direction) {
        List<Row> result = new ArrayList<>(ascending);
        if ("DESC".equals(direction)) Collections.reverse(result);
        return result;
    }

    public Long storageId() { return storageId; }

    @Step("Прибрати власний регіон, архівувати локацію та деактивувати тестові ресурси")
    public void cleanup() {
        List<Throwable> failures = new ArrayList<>();
        if (regionId != null) {
            try {
                Response response = regions.deleteRegion(UserRole.ADMIN, regionId);
                assertThat(response.statusCode()).isIn(200, 204, 404);
            } catch (Exception | AssertionError ex) { failures.add(ex); }
        }
        try {
            if (storageId != null) assertThat(storages.archiveStorage(UserRole.ADMIN, storageId)).isTrue();
        }
        catch (Exception | AssertionError ex) { failures.add(ex); }
        for (ResourceResponse resource : owned) {
            try {
                assertThat(resources.deactivate(UserRole.ADMIN, resource.getId()).statusCode()).isIn(200, 204);
            } catch (Exception | AssertionError ex) { failures.add(ex); }
        }
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("Resource sort fixture cleanup failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
