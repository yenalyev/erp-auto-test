package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceCategoryRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Reuses write-off lifecycle and verified cleanup; all locations, including supplier, are owned. */
public class LowValueWriteOffExportFixture extends LowValueWriteOffFixture {
    private final ApiExecutor api;
    private final List<Long> categories = new ArrayList<>();
    public long categoryId, controlCategoryId, clothOperation;

    public LowValueWriteOffExportFixture(TestContext context, ApiExecutor api) {
        super(context, api);
        this.api = api;
    }

    @Step("Підготувати ізольовану історію МШП та контрольні категорії")
    public void prepareExport() {
        prepare();
        categoryId = category("export-category");
        controlCategoryId = category("empty-category");
        recategorize(low, categoryId);
        recategorize(second, categoryId);
        recategorize(absent, controlCategoryId);
        var mixed = body(a.getId(), List.of(asset(second.getId(), 1.25), asset(low.getId(), .5)));
        mixed.put("comment", marker + " тканина, коментар Україна");
        clothOperation = create(UserRole.ADMIN, mixed);
    }

    private long category(String suffix) {
        var response = api.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_CREATE, UserRole.ADMIN,
                ResourceCategoryRequest.builder().name(marker + "-" + suffix).build());
        assertThat(response.statusCode()).isEqualTo(200);
        long id = response.jsonPath().getLong("id");
        categories.add(id);
        return id;
    }

    private void recategorize(ResourceResponse resource, long category) {
        Map<String, Object> body = new LinkedHashMap<>(resource(resource.getId()).jsonPath().getMap(""));
        body.put("measurementUnitId", resource.getUnit().getId());
        body.put("categoryId", category);
        assertThat(api.execute(ApiEndpointDefinition.RESOURCE_UPDATE_NAME, UserRole.ADMIN, body, resource.getId()).statusCode()).isEqualTo(200);
        assertThat(resource(resource.getId()).jsonPath().getLong("category.id")).isEqualTo(category);
    }

    public long createOperation(long storage, long resource, double amount, String label) {
        var body = body(storage, List.of(asset(resource, amount)));
        body.put("comment", marker + " " + label);
        return create(UserRole.ADMIN, body);
    }

    public List<Map<String, Object>> records(long storage) {
        var response = list(UserRole.ADMIN, Map.of("parentStorageId", storage, "resourceName", marker));
        assertThat(response.statusCode()).isEqualTo(200);
        return response.jsonPath().getList("content");
    }

    public Map<String, Object> record(long id, long storage) {
        var response = get(UserRole.ADMIN, id, storage);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.jsonPath().getMap("");
    }

    public Map<String, Object> snapshot() {
        return Map.of("a", records(a.getId()), "b", records(b.getId()),
                "aLow", stock(a.getId(), low.getId()), "aCloth", stock(a.getId(), second.getId()),
                "bLow", stock(b.getId(), low.getId()), "aRegular", stock(a.getId(), regular.getId()));
    }

    @Override @Step("Прибрати власні операції, надходження, локації, ресурси та категорії")
    public void cleanup() {
        try { super.cleanup(); }
        finally {
            // Archived resources still reference categories; detach only our resources before deletion.
            for (var resource : java.util.stream.Stream.of(low, second, absent).filter(Objects::nonNull).toList()) {
                var body = new LinkedHashMap<String, Object>(resource(resource.getId()).jsonPath().getMap(""));
                body.put("measurementUnitId", resource.getUnit().getId());
                body.put("categoryId", resource.getCategory().getId());
                assertThat(api.execute(ApiEndpointDefinition.RESOURCE_UPDATE_NAME, UserRole.ADMIN, body, resource.getId()).statusCode()).isEqualTo(200);
                assertThat(resource(resource.getId()).jsonPath().getBoolean("active")).isFalse();
            }
            for (long id : categories.reversed()) {
                assertThat(api.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_DELETE, UserRole.ADMIN, String.valueOf(id)).statusCode()).isIn(200, 204);
                var response = api.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.jsonPath().getList("id", Long.class)).doesNotContain(id);
            }
        }
    }
}
