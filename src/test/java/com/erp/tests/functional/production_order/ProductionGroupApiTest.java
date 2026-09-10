package com.erp.tests.functional.production_order;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.*;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.math.BigDecimal;
import java.util.*;

import static com.erp.api.endpoints.ApiEndpointDefinition.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated API lifecycle checks. ADMIN exercises business rules, not director RBAC. */
@Feature("Production groups")
public class ProductionGroupApiTest extends BaseFunctionalTest {
    private StorageFixture storages;
    private TechnologicalMapFixture maps;
    private ProductionOrderFixture orders;
    private UserFixture users;
    private long target, group, member1, member2, outside, resource, material, mapId;
    private long materialMapId;
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> resourceIds = new ArrayList<>();
    private ProductionOrderRequest createRequest;
    private long orderId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void seedGroup() {
        storages = new StorageFixture(testContext, apiExecutor);
        maps = new TechnologicalMapFixture(testContext, apiExecutor);
        orders = new ProductionOrderFixture(testContext, apiExecutor);
        target = orders.resolveTargetStorageId(UserRole.ADMIN);
        group = storages.createStorage(StorageDataFactory.childStorage(target, "PG-group")
                .productionGroup(true).build()).getId();
        member1 = storages.createChildStorage(group, "PG-member-1").getId();
        member2 = storages.createChildStorage(group, "PG-member-2").getId();
        outside = storages.createChildStorage(target, "PG-outside").getId();
        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        resource = resources.createUniqueResource("PG-output").getId();
        resourceIds.add(resource);
        material = resources.createUniqueResource("PG-material").getId();
        resourceIds.add(material);
        mapId = maps.createTechMapWithRequest(UserRole.ADMIN, TechnologicalMapRequest.builder()
                .name("PG-map-" + UUID.randomUUID()).type("PRODUCTION")
                .storageIds(Set.of(member1, member2, outside))
                .input(List.of(new ResourceUsageRequest(material, 1.0)))
                .output(List.of(new ResourceUsageRequest(resource, 1.0))).build()).getId();
        long raw = resources.createUniqueResource("PG-raw").getId();
        resourceIds.add(raw);
        materialMapId = maps.createTechMapWithRequest(UserRole.ADMIN, TechnologicalMapRequest.builder()
                .name("PG-material-map-" + UUID.randomUUID()).type("PRODUCTION").storageIds(Set.of(outside))
                .input(List.of(new ResourceUsageRequest(raw, 1.0)))
                .output(List.of(new ResourceUsageRequest(material, 1.0))).build()).getId();
    }

    @BeforeMethod(alwaysRun = true)
    public void createOrder() {
        createRequest = orders.buildCreateRequest(target, resource, 10);
        orderId = orders.create(UserRole.ADMIN, createRequest).getId();
        orderIds.add(orderId);
    }

    @AfterMethod(alwaysRun = true)
    public void removeOrders() {
        for (long id : List.copyOf(orderIds)) {
            String state = ok(call(PRODUCTION_ORDER_GET_BY_ID, null, id)).jsonPath().getString("state");
            if ("NEW".equals(state)) ok(orders.deleteRaw(UserRole.ADMIN, id));
            else if (!"CANCELLED".equals(state)) orders.cancel(UserRole.ADMIN, id);
            orderIds.remove(id);
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanupGroup() {
        try {
            removeOrders();
        } finally {
            try {
                if (users != null) {
                    apiExecutor.evictSessionForRole(UserRole.OWNER_1);
                    apiExecutor.evictSessionForRole(UserRole.OWNER_2);
                    users.deactivateTrackedUsers();
                }
                if (mapId != 0) {
                    for (long location : List.of(member1, member2, outside)) {
                        ok(maps.deactivateTechMap(UserRole.ADMIN, mapId, location));
                    }
                }
                if (materialMapId != 0) ok(maps.deactivateTechMap(UserRole.ADMIN, materialMapId, outside));
                for (long id : resourceIds) ok(call(RESOURCE_DEACTIVATE, null, id));
            } finally {
                if (storages != null) storages.deactivateTrackedStorages(UserRole.ADMIN);
            }
        }
    }

    @Test
    public void groupFlagPersistsAndGroupCannotBeTarget() {
        assertThat(storages.getById(UserRole.ADMIN, group).getProductionGroup()).isTrue();
        assertThat(ok(orders.getTargetLocationsRaw(UserRole.ADMIN)).jsonPath().getList("id", Long.class))
                .doesNotContain(group);
        Response response = call(PRODUCTION_ORDER_POST_CREATE, createRequest.toBuilder().targetStorageId(group).build());
        // Track even an unexpectedly accepted order so a failing assertion cannot leak it.
        if (response.statusCode() == 200) orderIds.add(response.jsonPath().getLong("id"));
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    public void decompositionReplacesMembersWithGroup() {
        Response response = ok(call(PRODUCTION_ORDER_POST_DECOMPOSE, plan(), orderId));
        assertThat(response.jsonPath().getList("blocks[0].items[0].groupOptions.id", Long.class)).contains(group);
        List<Long> offered = response.jsonPath().getList("blocks[0].items[0].options.storages.flatten().id", Long.class);
        assertThat(offered).contains(outside).doesNotContain(member1, member2, group);
        assertThat(ok(call(PRODUCTION_ORDER_GET_DELEGATIONS, null, orderId)).jsonPath().getList("$")).isEmpty();
    }

    @Test
    public void groupCannotAcquireOwnStockThroughInventory() {
        InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
        InventoryRequest request = InventoryRequest.builder()
                .resources(List.of(new ResourceUsageRequest(resource, 1.0))).build();
        inventory.openSession(group);
        Response response = null;
        try {
            response = inventory.conductInventoryRaw(group, UserRole.ADMIN, request);
            double stock = inventory.getResourceStock(group, resource, UserRole.ADMIN);
            org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
            softly.assertThat(response.statusCode()).as("Group stock write must be rejected").isEqualTo(400);
            softly.assertThat(stock).as("Production group must not own stock").isZero();
            softly.assertAll();
        } finally {
            try {
                if (response != null && response.statusCode() == 200) {
                    ok(inventory.conductInventoryRaw(group, UserRole.ADMIN, InventoryRequest.builder()
                            .resources(List.of()).build()));
                }
            } finally {
                inventory.closeSession(group);
            }
        }
    }

    @Test
    public void resendSamePlanKeepsRequestAndVersion() {
        long requestId = send(10);
        long version = version(requestId);
        assertThat(send(10)).isEqualTo(requestId);
        assertThat(version(requestId)).isEqualTo(version);
        assertThat(requestIds()).containsExactly(requestId);
        assertProgress(0, "WAITING");
        Response request = ok(call(PRODUCTION_DELEGATION_GET_PLAN, null, requestId));
        assertThat(request.jsonPath().getLong("delegation.productionOrderId")).isEqualTo(orderId);
        assertThat(request.jsonPath().getLong("delegation.resource.id")).isEqualTo(resource);
        assertThat(request.jsonPath().getList("memberStorages.id", Long.class)).containsExactlyInAnyOrder(member1, member2);
        assertThat(request.jsonPath().getList("delegation.destinations.storage.id", Long.class)).containsExactly(target);
    }

    @Test
    public void generateWhileWaitingIsRejectedWithoutTasks() {
        long id = send(10);
        assertThat(call(PRODUCTION_ORDER_POST_GENERATE, delegated(10), orderId).statusCode()).isEqualTo(400);
        assertThat(ok(call(PRODUCTION_ORDER_GET_TASKS, null, orderId)).jsonPath().getList("$")).isEmpty();
        assertThat(requestIds()).containsExactly(id);
        assertThat(snapshot().get("state")).isEqualTo("NEW");
    }

    @DataProvider
    public Object[][] invalidAnswers() {
        return new Object[][] {{"partial"}, {"empty"}, {"outside"}, {"group"}};
    }

    @Test(dataProvider = "invalidAnswers")
    public void invalidAnswerIsAtomic(String kind) {
        long id = send(10);
        Map<String, Object> before = snapshot();
        long base = version(id);
        DecompositionRequest answer = switch (kind) {
            case "partial" -> plan(produce(member1, 9));
            case "outside" -> plan(produce(outside, 10));
            case "group" -> delegated(10);
            default -> plan();
        };
        assertThat(answer(id, base, answer).statusCode()).as(kind).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(requestIds()).containsExactly(id);
        assertThat(version(id)).isEqualTo(base);
    }

    @Test
    public void completeAnswerMergesIntoSameOrderAndLeavesMaterialsForPlanner() {
        long id = send(10);
        Response own = ok(call(PRODUCTION_DELEGATION_DECOMPOSE, plan(), id));
        assertThat(own.jsonPath().getList("blocks[0].items.resource.id", Long.class)).containsExactly(resource);
        assertThat(own.jsonPath().getList("blocks[0].items[0].options.storages.flatten().id", Long.class))
                .containsExactlyInAnyOrder(member1, member2);
        ok(answer(id, version(id), plan(produce(member1, 4), produce(member2, 6))));
        assertThat(requestIds()).isEmpty();
        assertThat(ok(call(PRODUCTION_DELEGATION_QUEUE, null)).jsonPath().getList("id", Long.class)).doesNotContain(id);
        Response fetched = ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId));
        assertThat(fetched.jsonPath().getLong("id")).isEqualTo(orderId);
        assertThat(fetched.jsonPath().getString("state")).isEqualTo("NEW");
        assertThat(fetched.jsonPath().getList("decomposition.blocks[0].items[0].assignments.storageId", Long.class))
                .containsExactlyInAnyOrder(member1, member2);
        assertThat(fetched.jsonPath().getList("decomposition.blocks[0].items[0].assignments.amount", BigDecimal.class))
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("4"), new BigDecimal("6"));
        assertProgress(1, "PLANNED");
        DecompositionRequest stored = fetched.jsonPath().getObject("decomposition", DecompositionRequest.class);
        Response replay = ok(call(PRODUCTION_ORDER_POST_DECOMPOSE, stored, orderId));
        assertThat(replay.jsonPath().getBoolean("complete")).isFalse();
        assertThat(replay.jsonPath().getList("blocks.items.flatten().findAll { !it.complete }.resource.id", Long.class))
                .contains(material);
    }

    @Test
    public void dateAndDescriptionEditsKeepRequestAndAllowLoadedAnswer() {
        long id = send(10);
        long base = version(id);
        ok(call(PRODUCTION_ORDER_PUT_UPDATE, createRequest.toBuilder()
                .targetDate(createRequest.getTargetDate().plusDays(2)).description("PG edited description").build(), orderId));
        assertThat(requestIds()).containsExactly(id);
        assertThat(version(id)).isEqualTo(base);
        ok(answer(id, base, plan(produce(member1, 10))));
        assertThat(requestIds()).isEmpty();
    }

    @Test
    public void outputEditWithdrawsRequests() {
        long id = send(10);
        ok(call(PRODUCTION_ORDER_PUT_UPDATE, orders.buildCreateRequest(target, resource, 11), orderId));
        assertThat(requestIds()).isEmpty();
        assertThat(ok(call(PRODUCTION_DELEGATION_QUEUE, null)).jsonPath().getList("id", Long.class)).doesNotContain(id);
        assertThat(snapshot().get("delegationProgress")).isNull();
    }

    @Test
    public void changedPlanRejectsStaleAnswer() {
        long id = send(10);
        long base = version(id);
        // The request key survives a changed plan; a loaded director must reload its version.
        DecompositionRequest changed = plan(produce(outside, 2), delegate(8));
        addMaterialPlan(changed, 2);
        ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, changed, orderId));
        assertThat(version(id)).isGreaterThan(base);
        Map<String, Object> before = snapshot();
        Response rejected = answer(id, base, plan(produce(member1, 10)));
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(rejected.asString()).contains("Замовник змінив план");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(requestIds()).containsExactly(id);
    }

    @Test
    public void directorsAreScopedAndCannotEditPlannerOrder() {
        long id = send(10);
        users = new UserFixture(testContext, apiExecutor);
        assertThat(users.listRealmRoles()).extracting(RoleModelResponse::getName)
                .as("Group allocation uses the business owner role bound to a group location")
                .contains(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME);
        StorageResponse otherGroup = storages.createStorage(StorageDataFactory.childStorage(target, "PG-other-group")
                .productionGroup(true).build());
        UserFixture.RestrictedOwnerUser first = users.createRestrictedOwner(getPlaywrightSessionProvider(),
                storages.getById(UserRole.ADMIN, group));
        UserFixture.RestrictedOwnerUser second = users.createRestrictedOwner(getPlaywrightSessionProvider(), otherGroup);
        apiExecutor.setSessionForRole(UserRole.OWNER_1, first.username(), first.password());
        apiExecutor.setSessionForRole(UserRole.OWNER_2, second.username(), second.password());
        assertThat(ok(apiExecutor.execute(PRODUCTION_DELEGATION_QUEUE, UserRole.OWNER_1))
                .jsonPath().getList("id", Long.class)).containsExactly(id);
        assertThat(ok(apiExecutor.execute(PRODUCTION_DELEGATION_QUEUE, UserRole.OWNER_2))
                .jsonPath().getList("$" )).isEmpty();
        Map<String, Object> before = snapshot();
        for (ApiEndpointDefinition endpoint : List.of(PRODUCTION_DELEGATION_GET_PLAN,
                PRODUCTION_DELEGATION_DECOMPOSE, PRODUCTION_DELEGATION_ANSWER)) {
            Object body = endpoint == PRODUCTION_DELEGATION_GET_PLAN ? null
                    : endpoint == PRODUCTION_DELEGATION_ANSWER
                    ? Map.of("baseVersion", version(id), "blocks", plan(produce(member1, 10)).getBlocks()) : plan();
            assertThat(apiExecutor.execute(endpoint, UserRole.OWNER_2, body, id).statusCode())
                    .as("Foreign group: %s", endpoint).isEqualTo(403);
        }
        assertThat(apiExecutor.execute(PRODUCTION_ORDER_PUT_UPDATE, UserRole.OWNER_1, createRequest, orderId)
                .statusCode()).isEqualTo(403);
        assertThat(apiExecutor.execute(PRODUCTION_ORDER_DELETE, UserRole.OWNER_1, null, orderId)
                .statusCode()).isEqualTo(403);
        assertThat(snapshot()).isEqualTo(before);
        ok(apiExecutor.execute(PRODUCTION_DELEGATION_GET_PLAN, UserRole.OWNER_1, null, id));
        assertThat(apiExecutor.execute(PRODUCTION_DELEGATION_ANSWER, UserRole.OWNER_1,
                Map.of("baseVersion", version(id), "blocks", plan(produce(outside, 10)).getBlocks()), id)
                .statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        ok(apiExecutor.execute(PRODUCTION_DELEGATION_ANSWER, UserRole.OWNER_1,
                Map.of("baseVersion", version(id), "blocks", plan(produce(member1, 10)).getBlocks()), id));
        assertThat(requestIds()).isEmpty();
    }

    @Test
    public void plannerCanWithdrawRequestsByDeletingOrder() {
        long id = send(10);
        ok(orders.deleteRaw(UserRole.ADMIN, orderId));
        orderIds.remove(orderId);
        assertThat(ok(call(PRODUCTION_DELEGATION_QUEUE, null)).jsonPath().getList("id", Long.class)).doesNotContain(id);
        // The permission guard rejects a deleted entity before controller lookup.
        assertThat(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId).statusCode()).isEqualTo(403);
    }

    @Test
    public void targetEditWithdrawsRequests() {
        long id = send(10);
        ok(call(PRODUCTION_ORDER_PUT_UPDATE, createRequest.toBuilder().targetStorageId(outside).build(), orderId));
        assertThat(requestIds()).isEmpty();
        assertThat(ok(call(PRODUCTION_DELEGATION_QUEUE, null)).jsonPath().getList("id", Long.class)).doesNotContain(id);
    }

    @Test
    public void plannerCompletesNextRoundAndGeneratesSameOrder() {
        long id = send(10);
        ok(answer(id, version(id), plan(produce(member1, 10))));
        DecompositionRequest stored = ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId))
                .jsonPath().getObject("decomposition", DecompositionRequest.class);
        addMaterialPlan(stored);
        assertThat(ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, stored, orderId)).jsonPath().getList("$")).isEmpty();
        assertProgress(1, "PLANNED");
        Response generated = ok(call(PRODUCTION_ORDER_POST_GENERATE, stored, orderId));
        assertThat(generated.jsonPath().getList("$")).isNotEmpty();
        assertThat(generated.jsonPath().getList("storage.id", Long.class)).doesNotContain(group);
        assertThat(ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId)).jsonPath().getString("state"))
                .isEqualTo("IN_PROGRESS");
        assertThat(requestIds()).isEmpty();
    }

    @Test
    public void plannerCanOverwriteCompletedGroupAllocation() {
        long id = send(10);
        ok(answer(id, version(id), plan(produce(member1, 10))));
        DecompositionRequest replacement = plan(produce(outside, 10));
        addMaterialPlan(replacement);
        assertThat(ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, replacement, orderId)).jsonPath().getList("$")).isEmpty();
        Response fetched = ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId));
        assertThat(fetched.jsonPath().getList("decomposition.blocks[0].items[0].assignments.storageId", Long.class))
                .containsExactly(outside);
        assertThat(fetched.jsonPath().getObject("delegationProgress", Object.class)).isNull();
    }

    private void addMaterialPlan(DecompositionRequest plan) {
        addMaterialPlan(plan, 10);
    }

    private void addMaterialPlan(DecompositionRequest plan, int amount) {
        // The merger may already have persisted the unallocated next level.
        if (plan.getBlocks().size() > 1) plan.getBlocks().remove(1);
        plan.getBlocks().add(DecompositionRequest.DecompositionBlockRequest.builder().items(List.of(
                DecompositionRequest.DecompositionItemRequest.builder().resourceId(material).assignments(List.of(
                        DecompositionRequest.DecompositionAssignmentRequest.builder().storageId(outside)
                                .technologicalMapId(materialMapId).amount(BigDecimal.valueOf(amount)).build())).build())).build());
    }

    private long send(int amount) {
        Response response = ok(call(PRODUCTION_ORDER_SEND_DELEGATIONS, delegated(amount), orderId));
        assertThat(response.jsonPath().getList("$")).hasSize(1);
        return response.jsonPath().getLong("[0].id");
    }

    private long version(long id) { return ok(call(PRODUCTION_DELEGATION_GET_PLAN, null, id)).jsonPath().getLong("planVersion"); }
    private List<Long> requestIds() { return ok(call(PRODUCTION_ORDER_GET_DELEGATIONS, null, orderId)).jsonPath().getList("id", Long.class); }
    private Map<String, Object> snapshot() { return ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId)).jsonPath().getMap("$"); }
    private Response answer(long id, long version, DecompositionRequest plan) {
        return call(PRODUCTION_DELEGATION_ANSWER, Map.of("baseVersion", version, "blocks", plan.getBlocks()), id);
    }
    private void assertProgress(int planned, String status) {
        Response response = ok(call(PRODUCTION_ORDER_GET_BY_ID, null, orderId));
        assertThat(response.jsonPath().getInt("delegationProgress.total")).isEqualTo(1);
        assertThat(response.jsonPath().getInt("delegationProgress.planned")).isEqualTo(planned);
        assertThat(response.jsonPath().getList("delegationProgress.lines.status", String.class)).containsExactly(status);
    }
    private DecompositionRequest delegated(int amount) { return plan(delegate(amount)); }
    private DecompositionRequest.DecompositionAssignmentRequest delegate(int amount) {
        return DecompositionRequest.DecompositionAssignmentRequest.builder().storageId(group).amount(BigDecimal.valueOf(amount)).build();
    }
    private DecompositionRequest.DecompositionAssignmentRequest produce(long location, int amount) {
        return DecompositionRequest.DecompositionAssignmentRequest.builder().storageId(location)
                .technologicalMapId(mapId).amount(BigDecimal.valueOf(amount)).build();
    }
    private DecompositionRequest plan(DecompositionRequest.DecompositionAssignmentRequest... assignments) {
        return DecompositionRequest.builder().blocks(new ArrayList<>(List.of(
                DecompositionRequest.DecompositionBlockRequest.builder().items(List.of(
                        DecompositionRequest.DecompositionItemRequest.builder().resourceId(resource)
                                .assignments(List.of(assignments)).build())).build()))).build();
    }
    private Response call(ApiEndpointDefinition endpoint, Object body, Object... params) {
        return apiExecutor.execute(endpoint, UserRole.ADMIN, body, params);
    }
    private Response ok(Response response) {
        assertThat(response.statusCode()).as("API response: %s", response.asString()).isEqualTo(200);
        return response;
    }
}
