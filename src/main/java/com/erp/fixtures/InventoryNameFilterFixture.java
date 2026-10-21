package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import io.restassured.response.Response;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Real completed inventories on one owned location; no existing stocks or processes are edited. */
public class InventoryNameFilterFixture {
    private final ApiExecutor executor;
    public final InventoryFixture inventory;
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final RelocationFixture relocations;
    private final List<ResourceResponse> created = new ArrayList<>();
    public final String marker = "inv-name-" + UUID.randomUUID().toString().substring(0, 8);
    public StorageResponse storage;
    public ResourceResponse alpha, beta, cable;
    public InventoryProcessResponse mixed, cableOnly, manual, rejected;
    private Map<Long, InventoryProcessResponse> baseline;

    public InventoryNameFilterFixture(TestContext context, ApiExecutor executor) {
        this.executor = executor;
        inventory = new InventoryFixture(context, executor);
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
        relocations = new RelocationFixture(context, executor);
    }

    public void arrange() {
        storage = storages.createUniqueStorage(marker + "-");
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        alpha = resource(marker + " Акумулятор А-");
        beta = resource(marker + " Акумулятор Б-");
        cable = resource(marker + " Кабель-");
        for (var resource : created) relocations.seedExactStock(storage.getId(), resource.getId(), 10);
        mixed = complete(Map.of(alpha.getId(), 12.25, beta.getId(), 6.5, cable.getId(), 14.0));
        cableOnly = complete(Map.of(alpha.getId(), 12.25, beta.getId(), 6.5, cable.getId(), 15.0));
        manual = inventory.requestInventory(storage.getId(), UserRole.ADMIN, marker + " manual");
        inventory.approveInventory(storage.getId(), UserRole.ADMIN, marker);
        inventory.closeSession(storage.getId());
        rejected = inventory.requestInventory(storage.getId(), UserRole.ADMIN, marker + " rejected");
        inventory.rejectInventory(storage.getId(), UserRole.ADMIN, marker);
        baseline = processes(Map.of()).stream().collect(Collectors.toMap(InventoryProcessResponse::getId, p -> p));
        assertThat(baseline.keySet()).containsExactlyInAnyOrder(mixed.getId(), cableOnly.getId(), manual.getId(), rejected.getId());
        assertDiff(baseline.get(mixed.getId()), Map.of(alpha.getId(), 2.25, beta.getId(), -3.5, cable.getId(), 4.0));
        assertDiff(baseline.get(cableOnly.getId()), Map.of(cable.getId(), 1.0));
    }

    private ResourceResponse resource(String name) {
        var result = resources.createUniqueResource(name);
        created.add(result);
        return result;
    }

    private InventoryProcessResponse complete(Map<Long, Double> amounts) {
        var process = inventory.requestInventory(storage.getId(), UserRole.ADMIN, marker);
        inventory.approveInventory(storage.getId(), UserRole.ADMIN, marker);
        inventory.conductInventory(storage.getId(), UserRole.ADMIN, InventoryDataFactory.seedAmounts(amounts));
        return process;
    }

    public Map<String, Object> filters(Map<String, ?> extra) {
        var params = new HashMap<String, Object>();
        params.put("parentStorageId", storage.getId());
        params.put("states", List.of("CLOSED", "REJECTED"));
        params.putAll(extra);
        return params;
    }

    public Response list(Map<String, ?> extra) {
        var params = filters(Map.of("size", 100, "page", 0, "sort", "id,asc"));
        params.putAll(extra);
        var response = executor.executeWithQueryParams(ApiEndpointDefinition.STORAGE_INVENTORY_PROCESSES_GET,
                UserRole.ADMIN, params);
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    public List<InventoryProcessResponse> processes(Map<String, ?> extra) {
        return list(extra).jsonPath().getList("content", InventoryProcessResponse.class);
    }

    public Response export(Map<String, ?> extra) {
        var response = executor.executeWithQueryParams(ApiEndpointDefinition.STORAGE_INVENTORY_PROCESSES_EXPORT_GET,
                UserRole.ADMIN, filters(extra));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Content-Disposition")).contains("attachment", ".xlsx");
        return response;
    }

    public static void assertDiff(InventoryProcessResponse process, Map<Long, Double> expected) {
        var actual = new HashMap<Long, Double>();
        for (var row : process.getDiff()) {
            if (row.path("resourceId").isNull()) continue;
            assertThat(actual.put(row.path("resourceId").asLong(), row.path("diffAmount").asDouble()))
                    .as("No duplicate resource in process %s", process.getId()).isNull();
        }
        assertThat(actual).as("Exact filtered differences for process %s", process.getId()).isEqualTo(expected);
    }

    public void assertUnchanged() {
        if (baseline == null) return;
        var current = processes(Map.of()).stream().collect(Collectors.toMap(InventoryProcessResponse::getId, p -> p));
        assertThat(current).isEqualTo(baseline);
        var stocks = storages.getById(UserRole.ADMIN, storage.getId()).getItems().stream()
                .collect(Collectors.toMap(i -> i.getResource().getId(), StorageItemResponse::getAmount));
        assertThat(stocks).isEqualTo(Map.of(alpha.getId(), 12.25, beta.getId(), 6.5, cable.getId(), 15.0));
    }

    public void cleanup() {
        var errors = new ArrayList<Throwable>();
        if (storage != null) {
            attempt(errors, () -> inventory.ensureClosed(storage.getId()));
            for (var resource : created) attempt(errors, () -> inventory.removeResourceFromStorage(storage.getId(), resource.getId(), UserRole.ADMIN));
        }
        for (var resource : created) attempt(errors, () -> assertThat(resources.deactivate(UserRole.ADMIN, resource.getId()).statusCode()).isBetween(200, 299));
        if (storage != null) attempt(errors, () -> assertThat(storages.archiveStorage(UserRole.ADMIN, storage.getId())).isTrue());
        if (!errors.isEmpty()) {
            var error = new AssertionError("Inventory name filter fixture cleanup failed");
            errors.forEach(error::addSuppressed);
            throw error;
        }
    }

    private void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError error) { errors.add(error); }
    }
}
