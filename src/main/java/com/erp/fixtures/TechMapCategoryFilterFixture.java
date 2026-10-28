package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.models.query.TechnologicalMapListQuery;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Own categories/resources/maps only; restores the existing workspace's editing mode. */
public class TechMapCategoryFilterFixture extends BaseFixture {
    public final String marker = "tmcat-" + UUID.randomUUID().toString().substring(0, 8);
    public final long storageId = ConfigProvider.getOwner1StorageId();
    public final TechnologicalMapFixture maps;
    public final ResourceFixture resources;
    public ResourceCategoryResponse parent, categoryA, categoryB, emptyCategory;
    public ResourceResponse raw, productA, productB;
    public TechnologicalMapResponse a, b, parentMap, multi, archived, disassemble;
    public final List<TechnologicalMapResponse> ownedMaps = new ArrayList<>();
    private final List<ResourceResponse> ownedResources = new ArrayList<>();
    private final List<Long> ownedCategories = new ArrayList<>();
    private Long cleanupCategory;
    private StorageTechnologicalMapMode originalMode;

    public TechMapCategoryFilterFixture(TestContext context, ApiExecutor api) {
        super(context, api);
        maps = new TechnologicalMapFixture(context, api);
        resources = new ResourceFixture(context, api);
    }

    @Step("Створити ізольовані категорії та техкарти для фільтра виробу")
    public void prepare() {
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        cleanupCategory = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        var response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_MODE_GET,
                UserRole.ADMIN, String.valueOf(storageId));
        assertThat(response.statusCode()).as("Capture workspace mode").isEqualTo(200);
        originalMode = response.as(StorageTechnologicalMapModeResponse.class).getMode();
        parent = category("parent", null);
        categoryA = category("A", parent.getId());
        categoryB = category("B", null);
        emptyCategory = category("empty", null);
        raw = resource("raw", categoryA.getId());
        productA = resource("productA", categoryA.getId());
        productB = resource("productB", categoryB.getId());
        var parentProduct = resource("parentProduct", parent.getId());
        a = map("a", "PRODUCTION", productA);
        b = map("b", "PRODUCTION", productB);
        parentMap = map("parent", "PRODUCTION", parentProduct);
        multi = map("multi", "PRODUCTION", productA, productB);
        archived = map("archived", "PRODUCTION", productA);
        assertThat(maps.deactivateTechMap(UserRole.ADMIN, archived.getId(), storageId).statusCode())
                .as("Archive owned control map").isIn(200, 204);
        disassemble = map("disassemble", "DISASSEMBLE", productA);
        maps.updateNotes(UserRole.ADMIN, a.getId(), storageId, tag("a"));
        maps.updateNotes(UserRole.ADMIN, b.getId(), storageId, tag("b"));
        maps.updateNotes(UserRole.ADMIN, multi.getId(), storageId, tag("multi"));
    }

    private ResourceCategoryResponse category(String name, Long parentId) {
        var response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_CREATE, UserRole.ADMIN,
                ResourceCategoryRequest.builder().name(marker + "-" + name).parentId(parentId).build());
        assertThat(response.statusCode()).as("Create owned category").isIn(200, 201);
        var category = response.as(ResourceCategoryResponse.class);
        ownedCategories.addFirst(category.getId()); // children deleted before parents
        return category;
    }

    private ResourceResponse resource(String name, Long categoryId) {
        var resource = resources.createUniqueResource(marker + "-" + name, categoryId);
        ownedResources.add(resource);
        return resource;
    }

    private TechnologicalMapResponse map(String name, String type, ResourceResponse... output) {
        var map = maps.createTechMapWithRequest(UserRole.ADMIN, TechnologicalMapRequest.builder()
                .name(marker + "-" + name).type(type).storageIds(Set.of(storageId))
                .input(List.of(new ResourceUsageRequest(raw.getId(), 1.0)))
                .output(Arrays.stream(output).map(r -> new ResourceUsageRequest(r.getId(), 1.0)).toList())
                .build());
        ownedMaps.add(map);
        return map;
    }

    public String tag(String suffix) { return "#" + marker + "-" + suffix; }

    public TechnologicalMapListQuery query() {
        return TechnologicalMapListQuery.builder().storageId(storageId).name(marker)
                .type("PRODUCTION").isActive(true).build();
    }

    public List<Long> ids(UserRole role, TechnologicalMapListQuery query) {
        return maps.listByQuery(role, query).stream().map(TechnologicalMapResponse::getId).toList();
    }

    @Step("Прибрати власні дані та відновити режим локації")
    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        for (var map : ownedMaps) if (archived == null || !map.getId().equals(archived.getId())) {
            attempt(errors, () -> assertThat(maps.deactivateTechMap(UserRole.ADMIN,
                    map.getId(), storageId).statusCode()).as("Deactivate owned map").isIn(200, 204));
        }
        for (var resource : ownedResources) attempt(errors, () -> {
            resources.update(UserRole.ADMIN, resource.getId(), ResourceRequest.builder()
                    .name(resource.getName()).measurementUnitId(resource.getUnit().getId())
                    .categoryId(cleanupCategory).build());
            assertThat(resources.deactivate(UserRole.ADMIN, resource.getId()).statusCode())
                    .as("Deactivate owned resource").isIn(200, 204);
        });
        for (var id : ownedCategories) attempt(errors, () -> assertThat(apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_CATEGORY_DELETE, UserRole.ADMIN, null, id).statusCode())
                .as("Delete owned category %s", id).isIn(200, 204));
        if (originalMode != null) attempt(errors, () -> maps.setMode(storageId, originalMode));
        assertThat(errors).as("Category filter fixture cleanup failures").isEmpty();
    }

    private static void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (Throwable failure) { errors.add(failure); }
    }
}
