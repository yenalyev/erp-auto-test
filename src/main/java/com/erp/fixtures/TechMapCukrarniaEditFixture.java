package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.LocationProfileCatalog;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import io.restassured.response.Response;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Owned maps/resources below the real configured Cukrarnia; never edits business maps. */
public class TechMapCukrarniaEditFixture {
    public final String marker = "tm-cuk-" + UUID.randomUUID().toString().substring(0, 8);
    public final TechnologicalMapFixture maps;
    public StorageResponse root, child, grandchild;
    public TechnologicalMapResponse source, sibling;
    private final ApiExecutor executor;
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final Map<Long, Long> ownedMaps = new LinkedHashMap<>();
    private final List<Long> ownedResources = new ArrayList<>();
    private StorageTechnologicalMapMode originalRootMode;

    public TechMapCukrarniaEditFixture(TestContext context, ApiExecutor executor) {
        this.executor = executor;
        maps = new TechnologicalMapFixture(context, executor);
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
    }

    public void prepare(boolean enableRootEditing) {
        long rootId = LocationProfileCatalog.parentPool("TSUK_PARENT_UNITS").candidates().getFirst();
        root = storages.getById(UserRole.ADMIN, rootId);
        assertThat(root.getName()).as("Configured Cukrarnia workspace").containsIgnoringCase("цук");
        if (enableRootEditing) {
            Response mode = executor.execute(ApiEndpointDefinition.TECH_MAP_MODE_GET, UserRole.ADMIN,
                    null, String.valueOf(rootId));
            assertThat(mode.statusCode()).isEqualTo(200);
            originalRootMode = mode.as(StorageTechnologicalMapModeResponse.class).getMode();
            maps.setMode(rootId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        }
        child = storages.createProductionStorage(rootId, marker + "-child-");
        grandchild = storages.createProductionStorage(child.getId(), marker + "-leaf-");
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        List<ResourceResponse> ingredients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ResourceResponse resource = resources.createUniqueResource(marker + "-r" + i);
            ownedResources.add(resource.getId());
            ingredients.add(resource);
        }
        source = create(TechnologicalMapDataFactory.createProductionMapWithAlternativeGroup(
                ingredients, grandchild.getId()).toBuilder().name(marker + "-source").build());
        sibling = create(TechnologicalMapDataFactory.fromExisting(source)
                .name(marker + "-sibling").storageIds(Set.of(child.getId())).build());
    }

    public TechnologicalMapResponse create(TechnologicalMapRequest request) {
        TechnologicalMapResponse map = maps.createTechMapWithRequest(UserRole.ADMIN, request);
        track(map.getId(), request.getStorageIds().iterator().next());
        return map;
    }

    public void track(long mapId, long storageId) { ownedMaps.put(mapId, storageId); }

    /** Exact useTechnologicalMapForm payload: association row ids never leave the browser. */
    public static Map<String, Object> formPayload(TechnologicalMapRequest request) {
        return Map.of("name", request.getName(), "type", request.getType(), "storageIds", request.getStorageIds(),
                "input", request.getInput().stream().map(r -> Map.of("resourceId", r.getResourceId(), "amount", r.getAmount())).toList(),
                "output", request.getOutput().stream().map(r -> Map.of("resourceId", r.getResourceId(), "amount", r.getAmount())).toList(),
                "groups", request.getGroups().stream().map(g -> Map.of("name", g.getName(), "alternativeResources",
                        g.getAlternativeResources().stream().map(r -> Map.of("resourceId", r.getResourceId(),
                                "amount", r.getAmount(), "isDefault", r.getIsDefault())).toList())).toList());
    }

    public Response updateAsForm(UserRole role, long id, TechnologicalMapRequest request) {
        return executor.execute(ApiEndpointDefinition.TECH_MAP_UPDATE_NAME, role, formPayload(request), String.valueOf(id));
    }

    public TechnologicalMapResponse current(TechnologicalMapResponse map) {
        return maps.getById(UserRole.ADMIN, map.getId(), ownedMaps.get(map.getId()));
    }

    public void assertUnchanged(TechnologicalMapResponse map) {
        assertThat(current(map)).usingRecursiveComparison().isEqualTo(map);
        assertThat(maps.getVersionsByGroupId(UserRole.ADMIN, map.getGroupId(), ownedMaps.get(map.getId())))
                .extracting(TechnologicalMapResponse::getId).containsExactly(map.getId());
    }

    public void assertSiblingUnchanged() { assertUnchanged(sibling); }

    public void cleanup() {
        List<Throwable> failures = new ArrayList<>();
        // A failed assertion after a successful PUT must not strand the newly created version.
        for (var entry : List.copyOf(ownedMaps.entrySet())) {
            attempt(failures, () -> {
                var map = maps.getById(UserRole.ADMIN, entry.getKey(), entry.getValue());
                for (var version : maps.getVersionsByGroupId(UserRole.ADMIN, map.getGroupId(), entry.getValue())) {
                    for (var storage : version.getStorages()) {
                        if (!Objects.equals(storage.getId(), child.getId())
                                && !Objects.equals(storage.getId(), grandchild.getId())) continue;
                        maps.setMode(storage.getId(), StorageTechnologicalMapMode.EDIT_ALLOWED);
                        var response = maps.deactivateTechMap(UserRole.ADMIN, version.getId(), storage.getId());
                        assertThat(response.statusCode()).as("Deactivate owned map %s", version.getId()).isIn(200, 204);
                    }
                }
            });
        }
        for (long id : ownedResources) attempt(failures, () ->
                assertThat(resources.deactivate(UserRole.ADMIN, id).statusCode()).isIn(200, 204));
        // The server rejects archiving a parent with active children: archive bottom-up.
        for (StorageResponse storage : new StorageResponse[]{grandchild, child}) {
            if (storage == null) continue;
            attempt(failures, () -> {
                assertThat(storages.archiveStorage(UserRole.ADMIN, storage.getId()))
                        .as("Archive owned location %s", storage.getId()).isTrue();
                storages.untrackForCleanup(storage.getId());
            });
        }
        attempt(failures, () -> storages.deactivateTrackedStorages(UserRole.ADMIN));
        if (originalRootMode != null) attempt(failures, () -> maps.setMode(root.getId(), originalRootMode));
        assertThat(failures).as("All owned artifacts cleaned and root mode restored").isEmpty();
    }

    private void attempt(List<Throwable> failures, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError failure) { failures.add(failure); }
    }
}
