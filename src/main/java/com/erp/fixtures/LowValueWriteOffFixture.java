package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.response.*;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** All mutations belong to new resources/locations; successful negative POSTs are tracked too. */
public class LowValueWriteOffFixture {
    private final ApiExecutor api;
    private final TestContext context;
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final RelocationFixture relocations;
    private final InventoryFixture inventory;
    private final List<StorageResponse> locations = new ArrayList<>();
    private final List<ResourceResponse> ownedResources = new ArrayList<>();
    private final Map<Long, Long> operations = new LinkedHashMap<>();
    private final Map<Long, Long> receives = new LinkedHashMap<>();
    public final String marker = "mshp-" + UUID.randomUUID().toString().substring(0, 8);
    public ResourceResponse low, second, regular, absent;
    public StorageResponse a, b, external;
    public Long journalA, journalB;

    public LowValueWriteOffFixture(TestContext context, ApiExecutor api) {
        this.api = api;
        this.context = context;
        storages = new StorageFixture(context, api);
        resources = new ResourceFixture(context, api);
        relocations = new RelocationFixture(context, api);
        inventory = new InventoryFixture(context, api);
    }

    public static String today() { return LocalDate.now(ZoneId.of("Europe/Kyiv")).toString(); }

    public void prepare() {
        assertThat(System.getProperty("env", "dev")).as("Mutating fixture only in test environments").isIn("dev", "staging");
        recoverPreviousRun();
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        a = location("a", false);
        b = location("b", false);
        external = location("external", true);
        low = resource("gloves", "LOW_VALUE_ITEM");
        second = resource("cloth", "LOW_VALUE_ITEM");
        regular = resource("regular", "REGULAR");
        absent = resource("no-stock", "LOW_VALUE_ITEM");
        context.set(ContextKey.RELOCATION_SUPPLIER_ID,
                RelocationStockSeeder.resolveSupplierStorageId(api, UserRole.ADMIN));
        seed(a, low, 20, "A");
        seed(a, low, 10, "B");
        seed(a, second, 15, "C");
        seed(a, regular, 10, "D");
        seed(b, low, 20, "E");
        journalA = create(UserRole.ADMIN, body(a.getId(), List.of(asset(low.getId(), 1))));
        journalB = create(UserRole.ADMIN, body(b.getId(), List.of(asset(low.getId(), 2))));
        assertStock(a.getId(), low.getId(), 29);
        assertStock(b.getId(), low.getId(), 18);
        saveManifest();
    }

    private StorageResponse location(String suffix, boolean isExternal) {
        var request = StorageDataFactory.childStorage(1L, marker + "-" + suffix).build();
        request.setParentId(null);
        if (isExternal) request.setRelation(com.erp.enums.StorageRelation.EXTERNAL);
        var result = storages.createStorage(request);
        locations.add(result);
        saveManifest();
        return result;
    }

    private ResourceResponse resource(String suffix, String category) {
        ResourceResponse result = resources.createUniqueResource(marker + "-" + suffix);
        ownedResources.add(result);
        setCategory(result, category);
        saveManifest();
        return result;
    }

    public Response resource(long id) {
        Response response = api.execute(ApiEndpointDefinition.RESOURCE_GET_BY_ID, UserRole.ADMIN, String.valueOf(id));
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    public void setCategory(ResourceResponse resource, String category) {
        Map<String, Object> body = new LinkedHashMap<>(resource(resource.getId()).jsonPath().getMap(""));
        body.put("measurementUnitId", resource.getUnit().getId());
        body.put("categoryId", resource.getCategory().getId());
        body.put("assetCategory", category);
        Response response = api.execute(ApiEndpointDefinition.RESOURCE_UPDATE_NAME, UserRole.ADMIN, body, resource.getId());
        assertThat(response.statusCode()).as("Persist assetCategory: %s", response.asString()).isEqualTo(200);
        assertThat(resource(resource.getId()).jsonPath().getString("assetCategory")).isEqualTo(category);
    }

    private void seed(StorageResponse storage, ResourceResponse resource, double amount, String batch) {
        RelocationResponse result = relocations.createExternalReceive(UserRole.ADMIN, storage.getId(),
                resource.getId(), amount, marker + "-batch-" + batch);
        receives.put(result.getId(), storage.getId());
        saveManifest();
    }

    public static Map<String, Object> asset(long resource, double amount) {
        return new LinkedHashMap<>(Map.of("resourceId", resource, "amount", amount));
    }

    public Map<String, Object> body(long storage, List<Map<String, Object>> assets) {
        return new LinkedHashMap<>(Map.of("storageId", storage, "date", today(), "assets", assets,
                "comment", marker + " списання", "actors", Map.of("suppliedByName", "Автотест видав",
                        "suppliedByPosition", "Комірник", "acceptedByName", "Автотест прийняв",
                        "acceptedByPosition", "Бухгалтер")));
    }

    public Response post(UserRole role, Map<String, Object> body) {
        Response response = api.execute(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_CREATE, role, body);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            track(response.jsonPath().getLong("id"), ((Number)body.get("storageId")).longValue());
        }
        return response;
    }

    public long create(UserRole role, Map<String, Object> body) {
        Response response = post(role, body);
        assertThat(response.statusCode()).as("Create: %s", response.asString()).isEqualTo(200);
        return response.jsonPath().getLong("id");
    }

    public void track(long id, long storage) { operations.put(id, storage); saveManifest(); }

    public Response get(UserRole role, long id, long storage) {
        return api.execute(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_GET, role, null, id, storage);
    }

    public Response update(UserRole role, long id, Map<String, Object> body) {
        return api.execute(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_UPDATE, role, body, id);
    }

    public Response delete(UserRole role, long id, long storage) {
        Response response = api.execute(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_DELETE, role, null, id, storage);
        if (Objects.equals(operations.get(id), storage)
                && (response.statusCode() == 200 || response.statusCode() == 204 || response.statusCode() == 404)) {
            operations.remove(id);
            saveManifest();
        }
        return response;
    }

    public void remove(long id, long storage) {
        assertThat(delete(UserRole.ADMIN, id, storage).statusCode()).isIn(200, 204);
        assertThat(get(UserRole.ADMIN, id, storage).statusCode()).isEqualTo(404);
    }

    public Response list(UserRole role, Map<String, ?> filters) {
        Map<String, Object> query = new LinkedHashMap<>(Map.of("page", 0, "size", 100, "sort", "storageName,ASC"));
        query.putAll(filters);
        return api.executeWithQueryParams(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_LIST, role, query);
    }

    public List<Long> ids(Map<String, ?> filters) {
        Response response = list(UserRole.ADMIN, filters);
        assertThat(response.statusCode()).as("List: %s", response.asString()).isEqualTo(200);
        return response.jsonPath().getList("content.id", Long.class);
    }

    public Response available(UserRole role, long storage) {
        return api.executeWithQueryParams(ApiEndpointDefinition.LOW_VALUE_STORAGE_ITEMS, role,
                Map.of("assetCategory", "LOW_VALUE_ITEM"), storage);
    }

    public double stock(long storage, long resource) { return inventory.getResourceStock(storage, resource, UserRole.ADMIN); }
    public void assertStock(long storage, long resource, double expected) {
        assertThat(stock(storage, resource)).as("Exact stock location=%s resource=%s", storage, resource).isEqualTo(expected);
    }
    public List<StorageItemBatchResponse> batches(long storage, long resource) {
        Response response = api.executeWithQueryParams(ApiEndpointDefinition.STORAGE_ITEM_BATCHES_GET_BY_RESOURCE,
                UserRole.ADMIN, Map.of("storageId", storage, "resourceId", resource));
        assertThat(response.statusCode()).isEqualTo(200);
        // Current DTO has no origin field; the older shared inventory schema still requires it.
        List<StorageItemBatchResponse> result = response.jsonPath().getList("", StorageItemBatchResponse.class);
        assertThat(result).allSatisfy(batch -> {
            assertThat(batch.getBatchUuid()).isNotNull();
            assertThat(batch.getId()).isNotNull();
            assertThat(batch.getAmount()).isNotNull().isGreaterThanOrEqualTo(0);
        });
        return result;
    }

    public Response history(long storage, String name) {
        return api.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_OPERATION_HISTORY_GET, UserRole.ADMIN,
                Map.of("storageIds", storage, "from", today(), "to", today(), "productName", name));
    }

    private void saveManifest() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of(System.getProperty("test.output.directory", "target"),
                    "low-value-write-off-owned.json");
            java.nio.file.Files.createDirectories(path.getParent());
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(),
                    Map.of("environment", "dev", "marker", marker,
                            "locations", locations.stream().map(s -> Map.of("id", s.getId(), "name", s.getName())).toList(),
                            "resources", ownedResources.stream().map(r -> Map.of("id", r.getId(), "name", r.getName())).toList(),
                            "operations", operations, "receives", receives));
        } catch (java.io.IOException error) { throw new IllegalStateException("Save own cleanup manifest", error); }
    }

    /** Delete explicit zero-row tombstones, including rows hidden from ordinary inventory lists. */
    private void removeZeroRows(long storage, List<Long> resourceIds) {
        List<com.erp.models.request.ResourceUsageRequest> removed = new ArrayList<>();
        for (long id : resourceIds) {
            Response response = api.execute(ApiEndpointDefinition.STORAGE_INVENTORY_ITEM_BY_RESOURCE,
                    UserRole.ADMIN, null, storage, id);
            assertThat(response.statusCode()).isIn(200, 404);
            if (response.statusCode() == 404) continue;
            var item = response.as(StorageItemResponse.class);
            assertThat(item.getAmount()).as("Cleanup must not discard nonzero inventory").isZero();
            removed.add(com.erp.models.request.ResourceUsageRequest.builder().resourceId(id)
                    .amount(java.math.BigDecimal.ZERO).version(item.getVersion()).removed(true).build());
        }
        if (removed.isEmpty()) return;
        inventory.ensureClosed(storage);
        inventory.openSession(storage);
        try {
            inventory.conductInventory(storage, UserRole.ADMIN, com.erp.models.request.InventoryRequest.builder()
                    .resources(removed).knownResourceIds(removed.stream().map(com.erp.models.request.ResourceUsageRequest::getResourceId).toList()).build());
        } finally { inventory.closeSession(storage); }
        for (var row : removed) assertThat(api.execute(ApiEndpointDefinition.STORAGE_INVENTORY_ITEM_BY_RESOURCE,
                UserRole.ADMIN, null, storage, row.getResourceId()).statusCode()).isEqualTo(404);
    }

    private void recoverPreviousRun() {
        String path = System.getProperty("mshp.recovery");
        if (path == null || path.isBlank()) return;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var manifest = mapper.readTree(new java.io.File(path));
            assertThat(manifest.path("environment").asText()).isEqualTo("dev");
            assertThat(manifest.path("operations").size()).isZero();
            assertThat(manifest.path("receives").size()).isZero();
            String prefix = manifest.path("marker").asText();
            assertThat(prefix).startsWith("mshp-").hasSize(13);
            List<Long> ids = new ArrayList<>();
            for (var entry : manifest.path("resources")) {
                var resource = resources.getById(UserRole.ADMIN, entry.path("id").asLong());
                assertThat(resource.getName()).isEqualTo(entry.path("name").asText()).startsWith(prefix);
                ids.add(resource.getId());
            }
            List<StorageResponse> old = new ArrayList<>();
            for (var entry : manifest.path("locations")) {
                var storage = storages.getById(UserRole.ADMIN, entry.path("id").asLong());
                assertThat(storage.getName()).isEqualTo(entry.path("name").asText()).startsWith(prefix);
                old.add(storage);
            }
            // Validate every exact object and all amounts before the first mutation.
            for (var storage : old) for (long id : ids) {
                Response item = api.execute(ApiEndpointDefinition.STORAGE_INVENTORY_ITEM_BY_RESOURCE, UserRole.ADMIN, null, storage.getId(), id);
                assertThat(item.statusCode()).isIn(200, 404);
                if (item.statusCode() == 200) assertThat(item.jsonPath().getDouble("amount")).isZero();
            }
            for (var storage : old) {
                if (!Boolean.TRUE.equals(storage.getActive())) assertThat(storages.unarchive(UserRole.ADMIN, storage.getId()).statusCode()).isIn(200, 204);
                removeZeroRows(storage.getId(), ids);
                assertThat(storages.deactivate(UserRole.ADMIN, storage.getId()).statusCode()).isEqualTo(200);
                assertThat(storages.getById(UserRole.ADMIN, storage.getId()).getActive()).isFalse();
            }
            for (long id : ids) {
                if (Boolean.TRUE.equals(resources.getById(UserRole.ADMIN, id).getActive())) {
                    Response response = resources.deactivate(UserRole.ADMIN, id);
                    assertThat(response.statusCode()).as("Recover resource: %s", response.asString()).isIn(200, 204);
                }
                assertThat(resources.getById(UserRole.ADMIN, id).getActive()).isFalse();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(System.getProperty("test.output.directory", "target"),
                    "low-value-write-off-recovery.json").toFile(), Map.of("result", "SUCCESS", "locations", old.stream().map(StorageResponse::getId).toList(), "resources", ids));
        } catch (java.io.IOException e) { throw new IllegalStateException("Recover explicit own manifest", e); }
    }

    public void cleanup() {
        List<Throwable> failures = new ArrayList<>();
        for (var entry : new LinkedHashMap<>(operations).entrySet()) {
            try { remove(entry.getKey(), entry.getValue()); } catch (Throwable e) { failures.add(e); }
        }
        for (var entry : new ArrayList<>(receives.entrySet()).reversed()) {
            try {
                relocations.deleteRelocation(UserRole.ADMIN, entry.getKey(), entry.getValue());
                receives.remove(entry.getKey());
            } catch (Throwable e) { failures.add(e); }
        }
        for (var location : locations.reversed()) {
            try {
                removeZeroRows(location.getId(), ownedResources.stream().map(ResourceResponse::getId).toList());
                assertThat(inventory.listItems(location.getId(), UserRole.ADMIN))
                        .allSatisfy(item -> assertThat(item.getAmount()).isZero());
                assertThat(storages.archiveStorage(UserRole.ADMIN, location.getId())).isTrue();
                assertThat(storages.getById(UserRole.ADMIN, location.getId()).getActive()).isFalse();
                storages.untrackForCleanup(location.getId());
            } catch (Throwable e) { failures.add(e); }
        }
        for (var resource : ownedResources) {
            try {
                Response response = resources.deactivate(UserRole.ADMIN, resource.getId());
                assertThat(response.statusCode()).as("Cleanup resource %s: %s", resource.getId(), response.asString()).isIn(200, 204);
                assertThat(resources.getById(UserRole.ADMIN, resource.getId()).getActive()).isFalse();
            } catch (Throwable e) { failures.add(e); }
        }
        saveManifest();
        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError("МШП cleanup failed; see owned manifest");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }
}
