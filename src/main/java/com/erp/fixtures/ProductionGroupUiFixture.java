package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.*;
import static com.erp.api.endpoints.ApiEndpointDefinition.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Disposable two-group chain: output at A -> component at B -> raw material. */
public class ProductionGroupUiFixture implements AutoCloseable {
    private final ApiExecutor api;
    private final TestContext context;
    private final StorageFixture storages;
    private final UserFixture users;
    private final TechnologicalMapFixture maps;
    private final ProductionOrderFixture orders;
    private final InventoryFixture inventory;
    private final List<Long> resourceIds = new ArrayList<>(), orderIds = new ArrayList<>();
    private final Map<Long, Set<Long>> mapLocations = new LinkedHashMap<>();
    public StorageResponse target, groupA, groupB, memberA1, memberA2, memberB, outside;
    public ResourceResponse output, component;
    public long outputMap, componentMap;
    private UserFixture.BusinessActor ownerA, ownerB;
    private PlaywrightSessionProvider provider;

    public ProductionGroupUiFixture(TestContext context, ApiExecutor api) {
        this.context = context;
        this.api = api;
        storages = new StorageFixture(context, api);
        users = new UserFixture(context, api);
        maps = new TechnologicalMapFixture(context, api);
        orders = new ProductionOrderFixture(context, api);
        inventory = new InventoryFixture(context, api);
    }

    public void seed(PlaywrightSessionProvider provider) {
        this.provider = provider;
        target = storages.createChildStorage(orders.resolveTargetStorageId(UserRole.ADMIN), "PGUI-target");
        groupA = group("PGUI-A"); groupB = group("PGUI-B");
        memberA1 = productionLocation(groupA.getId(), "PGUI-A1");
        memberA2 = productionLocation(groupA.getId(), "PGUI-A2");
        memberB = productionLocation(groupB.getId(), "PGUI-B1");
        outside = productionLocation(target.getId(), "PGUI-outside");
        ResourceFixture resources = new ResourceFixture(context, api);
        resources.fetchSharedUnit(1); resources.fetchSharedResourceCategory();
        output = resource(resources, "PGUI-output");
        component = resource(resources, "PGUI-component");
        ResourceResponse raw = resource(resources, "PGUI-raw");
        outputMap = map(component, output, Set.of(memberA1.getId(), memberA2.getId(), outside.getId()));
        componentMap = map(raw, component, Set.of(memberB.getId()));
    }

    public UserFixture.BusinessActor directorForGroup(long groupId) {
        if (groupId == groupA.getId()) {
            if (ownerA == null) {
                ownerA = users.createBusinessActor(provider, BusinessRole.PRODUCTION_GROUP_DIRECTOR, List.of(groupA));
            }
            return ownerA;
        }
        if (groupId == groupB.getId()) {
            if (ownerB == null) {
                ownerB = users.createBusinessActor(provider, BusinessRole.PRODUCTION_GROUP_DIRECTOR, List.of(groupB));
            }
            return ownerB;
        }
        throw new IllegalArgumentException("Unknown production group id: " + groupId);
    }

    private StorageResponse group(String prefix) {
        return storages.createStorage(StorageDataFactory.childStorage(target.getId(), prefix).productionGroup(true).build());
    }
    private StorageResponse productionLocation(long parentId, String prefix) {
        return storages.createStorage(StorageDataFactory.childStorage(
                        parentId, prefix, UnitType.PRODUCTION, StorageRelation.INTERNAL)
                .features(Set.of(
                        LocationFeature.RELOCATIONS,
                        LocationFeature.PRODUCE,
                        LocationFeature.EQUIPMENT,
                        LocationFeature.TASKS))
                .build());
    }
    private ResourceResponse resource(ResourceFixture fixture, String name) {
        ResourceResponse result = fixture.createUniqueResource(name);
        resourceIds.add(result.getId()); return result;
    }
    private long map(ResourceResponse input, ResourceResponse output, Set<Long> locations) {
        long id = maps.createTechMapWithRequest(UserRole.ADMIN, TechnologicalMapRequest.builder()
                .name("PGUI-map-" + UUID.randomUUID()).type("PRODUCTION").storageIds(locations)
                .input(List.of(new ResourceUsageRequest(input.getId(), 1.0)))
                .output(List.of(new ResourceUsageRequest(output.getId(), 1.0))).build()).getId();
        mapLocations.put(id, locations); return id;
    }
    public long createOrder() {
        long id = orders.create(UserRole.ADMIN, orders.buildCreateRequest(target.getId(), output.getId(), 10)).getId();
        orderIds.add(id); return id;
    }
    public void seedOutputInputStock() {
        inventory.resetResourceStock(outside.getId(), component.getId(), 10, UserRole.ADMIN);
    }
    public StorageResponse newGroupCandidateWithChild() {
        StorageResponse candidate = storages.createChildStorage(target.getId(), "PGUI-form");
        storages.createChildStorage(candidate.getId(), "PGUI-form-member");
        return candidate;
    }
    public static String allocationPermission(long ignoredGroupId) {
        return "production-order.allocate";
    }

    public List<StorageResponse> receiveLocations() {
        StorageResponse group = storages.createStorage(StorageDataFactory.externalStorage(target.getId(), "PGUI-input-group")
                .productionGroup(true).build());
        StorageResponse member = storages.createStorage(StorageDataFactory.externalStorage(group.getId(), "PGUI-input-member")
                .build());
        StorageResponse outside = storages.createStorage(StorageDataFactory.externalStorage(target.getId(), "PGUI-input-outside")
                .build());
        return List.of(group, member, outside);
    }
    public long send(long orderId) {
        return ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, plan(output.getId(), assignment(groupA.getId(), null, 10)), orderId))
                .jsonPath().getLong("[0].id");
    }
    public long requestId(long orderId, long groupId) {
        return ok(call(PRODUCTION_ORDER_GET_DELEGATIONS, null, orderId)).jsonPath()
                .getLong("find { it.groupStorage.id == " + groupId + " }.id");
    }
    public void changeSentPlan(long orderId) {
        DecompositionRequest request = plan(output.getId(), assignment(groupA.getId(), null, 8),
                assignment(outside.getId(), outputMap, 2));
        request.getBlocks().add(plan(component.getId(), assignment(groupB.getId(), null, 2)).getBlocks().getFirst());
        ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, request, orderId));
    }
    public Response getOrder(long id) { return ok(call(PRODUCTION_ORDER_GET_BY_ID, null, id)); }
    public void answerA(long requestId) {
        long version = ok(call(PRODUCTION_DELEGATION_GET_PLAN, null, requestId)).jsonPath().getLong("planVersion");
        ok(call(PRODUCTION_DELEGATION_ANSWER, Map.of("baseVersion", version, "blocks",
                plan(output.getId(), assignment(memberA1.getId(), outputMap, 10)).getBlocks()), requestId));
    }
    public Response call(ApiEndpointDefinition endpoint, Object body, Object... params) {
        return api.execute(endpoint, UserRole.ADMIN, body, params);
    }
    public static Response ok(Response response) {
        assertThat(response.statusCode()).as("API: %s", response.asString()).isEqualTo(200); return response;
    }
    public static DecompositionRequest.DecompositionAssignmentRequest assignment(long storage, Long map, int amount) {
        return DecompositionRequest.DecompositionAssignmentRequest.builder().storageId(storage).technologicalMapId(map)
                .amount(BigDecimal.valueOf(amount)).build();
    }
    public static DecompositionRequest plan(long resource, DecompositionRequest.DecompositionAssignmentRequest... assignments) {
        return DecompositionRequest.builder().blocks(new ArrayList<>(List.of(
                DecompositionRequest.DecompositionBlockRequest.builder().items(List.of(
                        DecompositionRequest.DecompositionItemRequest.builder().resourceId(resource)
                                .assignments(List.of(assignments)).build())).build()))).build();
    }
    public void cleanupOrders() {
        for (long id : List.copyOf(orderIds)) {
            String state = getOrder(id).jsonPath().getString("state");
            if ("NEW".equals(state)) ok(orders.deleteRaw(UserRole.ADMIN, id));
            else if ("IN_PROGRESS".equals(state) || "READY_TO_DELIVER".equals(state)) {
                orders.cancel(UserRole.ADMIN, id);
            }
            else assertThat(state).as("Cleanup order %s state", id).isEqualTo("CANCELLED");
            orderIds.remove(id);
        }
    }
    @Override public void close() {
        org.assertj.core.api.SoftAssertions errors = new org.assertj.core.api.SoftAssertions();
        cleanup(errors, this::cleanupOrders);
        cleanup(errors, users::deactivateTrackedUsers);
        if (outside != null && component != null) {
            cleanup(errors, () -> inventory.removeResourceFromStorage(
                    outside.getId(), component.getId(), UserRole.ADMIN));
        }
        mapLocations.forEach((map, locations) -> locations.forEach(location ->
                cleanup(errors, () -> ok(maps.deactivateTechMap(UserRole.ADMIN, map, location)))));
        resourceIds.forEach(id -> cleanup(errors, () -> ok(call(RESOURCE_DEACTIVATE, null, id))));
        cleanup(errors, () -> storages.deactivateTrackedStorages(UserRole.ADMIN));
        errors.assertAll();
    }
    private void cleanup(org.assertj.core.api.SoftAssertions errors, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError error) { errors.fail("Cleanup failed", error); }
    }
}
