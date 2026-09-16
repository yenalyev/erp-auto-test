package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.OrderRelocationTaskState;
import com.erp.enums.OrderState;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.OrderRelocationTaskFixture;
import com.erp.fixtures.ProductionOrderFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderRelocationTaskResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ProductionOrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("Order_Admin-ROLE")
public class OrderAdminApiTest extends OrderApiTestBase {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final UserRole SOURCE_KEEPER = UserRole.ORDER_SOURCE_KEEPER;
    private static final UserRole ISOLATED_GATHERER = UserRole.ORDER_ISOLATED_GATHERER;

    private UserFixture userFixture;
    private UserFixture.BusinessActor orderAdmin;
    private UserFixture.BusinessActor sourceKeeper;
    private UserFixture.BusinessActor isolatedGatherer;
    private ProductionOrderFixture productionOrderFixture;
    private ResourceFixture resourceFixture;
    private TechnologicalMapFixture technologicalMapFixture;
    private OrderRelocationTaskFixture relocationTaskFixture;
    private StorageFixture storageFixture;
    private StorageResponse sourceStorage;
    private StorageResponse isolatedGatheringStorage;
    private ResourceResponse productionResource;
    private TechnologicalMapResponse productionTechMap;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupOrderAdmin() {
        userFixture = new UserFixture(testContext, apiExecutor);
        productionOrderFixture = new ProductionOrderFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        technologicalMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        relocationTaskFixture = new OrderRelocationTaskFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        productionResource = resourceFixture.createUniqueResource("ord-production-");
        relocationFixture.seedExactStock(
                requesterStorageId, productionResource.getId(), 1.0, UserRole.ADMIN);
        inventoryFixture.resetResourceStock(
                requesterStorageId, productionResource.getId(), 0.0, UserRole.ADMIN);
        StorageResponse requesterStorage = storageFixture.getById(UserRole.ADMIN, requesterStorageId);
        orderAdmin = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(requesterStorage));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());

        long availabilityRoot = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (availabilityRoot <= 0) {
            availabilityRoot = gatheringStorageId;
        }
        sourceStorage = storageFixture.createChildStorage(availabilityRoot, "ord-rel-source-");
        relocationFixture.ensureStock(sourceStorage.getId(), resourceId, DEFAULT_SEED_STOCK);
        sourceKeeper = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.UNIT_KOMIRNIK, List.of(sourceStorage));
        apiExecutor.setSessionForRole(SOURCE_KEEPER, sourceKeeper.username(), sourceKeeper.password());

        isolatedGatheringStorage = storageFixture.createChildStorage(
                availabilityRoot, "ord-gathering-");
        isolatedGatheringStorage = storageFixture.ensureOrderHub(
                UserRole.ADMIN, isolatedGatheringStorage.getId());
        isolatedGatherer = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.UNIT_KOMIRNIK,
                List.of(isolatedGatheringStorage));
        apiExecutor.setSessionForRole(
                ISOLATED_GATHERER, isolatedGatherer.username(), isolatedGatherer.password());
        productionTechMap = technologicalMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                                "ord-production-map",
                                List.of(new ResourceUsageRequest(resourceId, 1.0)),
                                List.of(new ResourceUsageRequest(productionResource.getId(), 1.0)),
                                Set.of(isolatedGatheringStorage.getId()))
                        .build());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupOrderAdmin() {
        if (orderAdmin != null) {
            apiExecutor.evictSessionForRole(ORDER_ADMIN);
        }
        if (sourceKeeper != null) {
            apiExecutor.evictSessionForRole(SOURCE_KEEPER);
        }
        if (isolatedGatherer != null) {
            apiExecutor.evictSessionForRole(ISOLATED_GATHERER);
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        if (storageFixture != null && sourceStorage != null) {
            storageFixture.archiveStorage(UserRole.ADMIN, sourceStorage.getId());
        }
        if (technologicalMapFixture != null
                && productionTechMap != null
                && isolatedGatheringStorage != null) {
            technologicalMapFixture.deactivateTechMap(
                    UserRole.ADMIN, productionTechMap.getId(), isolatedGatheringStorage.getId());
        }
        if (storageFixture != null && isolatedGatheringStorage != null) {
            storageFixture.archiveStorage(UserRole.ADMIN, isolatedGatheringStorage.getId());
        }
        if (resourceFixture != null && productionResource != null) {
            resourceFixture.deactivate(UserRole.ADMIN, productionResource.getId());
        }
    }

    @Test(priority = 1)
    @TestCaseId(value = "TC-ORD-ADMIN-001", roles = BusinessRole.ORDER_ADMIN)
    @Story("Order administrator permission boundary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Order_Admin-ROLE керує всіма замовленнями, читає ВЗ, але не створює ВЗ і не відправляє зі складу.")
    public void orderAdminPermissionsAreSeparatedFromGlobalAdminAndGatherer() {
        UserMeResponse me = userFixture.getMe(ORDER_ADMIN);

        // /users/me exposes the normalized realm role; the catalog/creation request keeps
        // the exact Keycloak role name Order_Admin-ROLE.
        assertThat(me.getRoles()).contains("Order_Admin");
        assertThat(me.getPermissions())
                .contains("order::all::read", "order::all::update", "order::all::manage")
                .contains("production-order::all::read")
                .doesNotContain("production-order::all::create");

        Response productionList = productionOrderFixture.getPageRaw(ORDER_ADMIN, gatheringStorageId);
        assertThat(productionList.statusCode()).isEqualTo(200);

        Response createProductionDenied = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_ORDER_POST_CREATE,
                ORDER_ADMIN,
                productionOrderFixture.buildCreateRequest(gatheringStorageId, resourceId, 1.0));
        assertThat(createProductionDenied.statusCode()).isEqualTo(403);
    }

    @Test(priority = 2)
    @TestCaseId(value = "TC-ORD-ADMIN-002", roles = BusinessRole.ORDER_ADMIN)
    @Story("Order administrator lifecycle")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Комірник створює; Order Admin приймає, призначає збір, частково бронює і ставить «Готово до доставки»; комірник збору відправляє.")
    public void orderAdminManagesPartialOrderAndGathererShipsIt() {
        double partialQty = 2.0;
        OrderResponse created = orderFixture.createOrder(REQUESTER);
        assertThat(created.getState()).isEqualTo(OrderState.NEW);

        OrderResponse inProgress = orderFixture.takeToWork(
                ORDER_ADMIN, created.getId(), requesterStorageId);
        assertThat(inProgress.getState()).isEqualTo(OrderState.IN_PROGRESS);

        Long gatheringId = orderFixture.resolveGatheringStorageId(
                ORDER_ADMIN, created.getId(), requesterStorageId);
        relocationFixture.ensureStock(gatheringId, resourceId, DEFAULT_SEED_STOCK);
        orderFixture.setGathering(ORDER_ADMIN, created.getId(), requesterStorageId, gatheringId);

        BookingResponse booking = orderFixture.book(
                ORDER_ADMIN, created.getId(), requesterStorageId, resourceId, partialQty);
        assertThat(booking.getAmount().doubleValue()).isEqualTo(partialQty);
        assertThat(orderFixture.getById(REQUESTER, created.getId()).getState())
                .isEqualTo(OrderState.IN_PROGRESS);

        OrderResponse ready = orderFixture.markReadyToDeliver(
                ORDER_ADMIN, created.getId(), requesterStorageId);
        assertThat(ready.getState()).isEqualTo(OrderState.READY_TO_DELIVER);

        RelocationOutputRequest send = OrderDataFactory.buildShipRequest(
                created.getId(), gatheringId, requesterStorageId, resourceId, partialQty);
        Response orderAdminCannotShip = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, ORDER_ADMIN, send);
        assertThat(orderAdminCannotShip.statusCode()).isEqualTo(403);

        Response gathererShips = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);
        assertThat(gathererShips.statusCode())
                .as("body=%s", gathererShips.body().asString())
                .isEqualTo(200);
        RelocationResponse shipment = gathererShips.as(RelocationResponse.class);
        assertThat(orderFixture.getById(REQUESTER, created.getId()).getState())
                .isEqualTo(OrderState.DONE);

        RelocationResponse received = relocationFixture.resolve(
                REQUESTER, shipment.getId(), requesterStorageId, RelocationState.FINISHED);
        assertThat(received.getState()).isEqualTo(RelocationState.FINISHED);
        assertThat(orderFixture.getById(REQUESTER, created.getId()).getState())
                .as("DONE currently means shipped; receiving does not change the order state")
                .isEqualTo(OrderState.DONE);
    }

    @Test(priority = 3)
    @TestCaseId(value = "TC-ORD-ADMIN-003", roles = {
            BusinessRole.UNIT_KOMIRNIK, BusinessRole.ORDER_ADMIN})
    @Story("Cross-location order fulfillment")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin створює запит на переміщення; комірник джерела відправляє, збір приймає; після бронювання замовлення відправляється і приймається точкою.")
    public void relocationTaskSuppliesGatheringBeforeFinalShipment() {
        double quantity = 5.0;
        resetIsolatedGatheringStock();
        OrderResponse order = orderFixture.createOrder(
                REQUESTER, requesterStorageId, resourceId, quantity);
        orderFixture.takeToWork(ORDER_ADMIN, order.getId(), requesterStorageId);
        Long gatheringId = isolatedGatheringStorage.getId();
        orderFixture.setGathering(ORDER_ADMIN, order.getId(), requesterStorageId, gatheringId);
        relocationFixture.ensureStock(sourceStorage.getId(), resourceId, quantity);

        long orderLineId = order.getLines().getFirst().getId();
        Response requesterDenied = relocationTaskFixture.createRaw(
                REQUESTER,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(sourceStorage.getId(), orderLineId, quantity));
        assertThat(requesterDenied.statusCode()).isEqualTo(403);

        OrderRelocationTaskResponse task = relocationTaskFixture.create(
                ORDER_ADMIN,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(sourceStorage.getId(), orderLineId, quantity));
        assertThat(task.getState()).isEqualTo(OrderRelocationTaskState.NEW);
        assertThat(task.getSourceStorage().getId()).isEqualTo(sourceStorage.getId());
        assertThat(task.getGatheringStorage().getId()).isEqualTo(gatheringId);
        assertThat(readBookedAmount(sourceStorage.getId())).isEqualTo(quantity);
        assertThat(relocationTaskFixture.getForSource(SOURCE_KEEPER, sourceStorage.getId()))
                .extracting(OrderRelocationTaskResponse::getId)
                .contains(task.getId());

        RelocationOutputRequest inboundRequest = RelocationOutputRequest.builder()
                .relocationTaskId(task.getId())
                .senderId(sourceStorage.getId())
                .recipientId(gatheringId)
                .description("order relocation task")
                .date(LocalDate.now())
                .items(List.of(ResourceUsageRequest.builder()
                        .resourceId(resourceId)
                        .amount(BigDecimal.valueOf(quantity))
                        .build()))
                .sendingPersonName("Source Keeper")
                .sendingPersonRank("Комірник")
                .receivingPersonName("Gathering Keeper")
                .receivingPersonRank("Комірник")
                .build();
        Response inboundSend = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, SOURCE_KEEPER, inboundRequest);
        assertThat(inboundSend.statusCode())
                .as("body=%s", inboundSend.body().asString())
                .isEqualTo(200);
        RelocationResponse inbound = inboundSend.as(RelocationResponse.class);
        OrderRelocationTaskResponse shippedTask = findTask(order.getId(), task.getId());
        assertThat(shippedTask.getState()).isEqualTo(OrderRelocationTaskState.SHIPPED);
        assertThat(shippedTask.getRelocationId()).isEqualTo(inbound.getId());

        RelocationResponse gatheringReceived = relocationFixture.resolve(
                ISOLATED_GATHERER, inbound.getId(), gatheringId, RelocationState.FINISHED);
        assertThat(gatheringReceived.getState()).isEqualTo(RelocationState.FINISHED);
        assertThat(findTask(order.getId(), task.getId()).getState())
                .isEqualTo(OrderRelocationTaskState.DONE);
        assertThat(readBookedAmount(sourceStorage.getId())).isZero();

        assertThat(orderFixture.getBookings(ORDER_ADMIN, order.getId()))
                .filteredOn(booking -> booking.getResourceId().equals(resourceId))
                .extracting(booking -> booking.getAmount().doubleValue())
                .containsExactly(quantity);
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState())
                .isEqualTo(OrderState.READY_TO_DELIVER);
        RelocationResponse finalShipment = orderFixture.shipOrder(
                ISOLATED_GATHERER, order.getId(), gatheringId, requesterStorageId, resourceId, quantity);
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState())
                .isEqualTo(OrderState.DONE);
        assertThat(relocationFixture.resolve(
                REQUESTER, finalShipment.getId(), requesterStorageId, RelocationState.FINISHED).getState())
                .isEqualTo(RelocationState.FINISHED);
    }

    @Test(priority = 4)
    @TestCaseId(value = "TC-ORD-ADMIN-005", roles = BusinessRole.ORDER_ADMIN)
    @Story("Relocation task cancellation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Запит не може перевищувати дефіцит; скасування NEW-запиту звільняє резерв на локації-джерелі.")
    public void relocationTaskCancellationReleasesSourceReservation() {
        double quantity = 5.0;
        double requested = 3.0;
        resetIsolatedGatheringStock();
        OrderResponse order = orderFixture.createOrder(
                REQUESTER, requesterStorageId, resourceId, quantity);
        orderFixture.takeToWork(ORDER_ADMIN, order.getId(), requesterStorageId);
        Long gatheringId = isolatedGatheringStorage.getId();
        orderFixture.setGathering(ORDER_ADMIN, order.getId(), requesterStorageId, gatheringId);
        relocationFixture.ensureStock(sourceStorage.getId(), resourceId, quantity);
        long orderLineId = order.getLines().getFirst().getId();

        Response exceedsShortfall = relocationTaskFixture.createRaw(
                ORDER_ADMIN,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(sourceStorage.getId(), orderLineId, quantity + 1));
        assertThat(exceedsShortfall.statusCode()).isEqualTo(400);

        OrderRelocationTaskResponse task = relocationTaskFixture.create(
                ORDER_ADMIN,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(sourceStorage.getId(), orderLineId, requested));
        assertThat(readBookedAmount(sourceStorage.getId())).isEqualTo(requested);

        OrderRelocationTaskResponse cancelled = relocationTaskFixture.cancel(
                ORDER_ADMIN, order.getId(), task.getId(), requesterStorageId);
        assertThat(cancelled.getState()).isEqualTo(OrderRelocationTaskState.CANCELLED);
        assertThat(readBookedAmount(sourceStorage.getId())).isZero();
    }

    @Test(priority = 5)
    @TestCaseId(value = "TC-ORD-ADMIN-004", roles = BusinessRole.ORDER_ADMIN)
    @Story("Production shortage handoff")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Глобальний Admin створює ВЗ на локацію збору; Order Admin може прив'язати й відв'язати його від замовлення, але не може створити ВЗ.")
    public void orderAdminLinksProductionOrderCreatedByGlobalAdmin() {
        double quantity = 4.0;
        resetIsolatedGatheringStock();
        OrderResponse order = orderFixture.createOrder(
                REQUESTER, requesterStorageId, productionResource.getId(), quantity);
        orderFixture.takeToWork(ORDER_ADMIN, order.getId(), requesterStorageId);
        Long gatheringId = isolatedGatheringStorage.getId();
        orderFixture.setGathering(ORDER_ADMIN, order.getId(), requesterStorageId, gatheringId);

        Response shortfall = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_SHORTFALL,
                ORDER_ADMIN,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(shortfall.statusCode()).as("body=%s", shortfall.body().asString()).isEqualTo(200);
        assertThat(shortfall.body().asString()).contains(String.valueOf(productionResource.getId()));

        ProductionOrderResponse productionOrder = productionOrderFixture.create(
                UserRole.ADMIN,
                productionOrderFixture.buildCreateRequest(
                        gatheringId, productionResource.getId(), quantity));
        try {
            Response attachable = apiExecutor.execute(
                    ApiEndpointDefinition.ORDER_GET_ATTACHABLE_PRODUCTION_ORDERS,
                    ORDER_ADMIN,
                    null,
                    order.getId(),
                    requesterStorageId);
            assertThat(attachable.statusCode())
                    .as("body=%s", attachable.body().asString())
                    .isEqualTo(200);
            assertThat(attachable.body().asString())
                    .contains(String.valueOf(productionOrder.getId()));

            Response linked = productionOrderFixture.linkOrderRaw(
                    ORDER_ADMIN, productionOrder.getId(), order.getId());
            assertThat(linked.statusCode())
                    .as("body=%s", linked.body().asString())
                    .isEqualTo(200);
            Response linkedToOrder = productionOrderFixture.getLinkedToOrderRaw(ORDER_ADMIN, order.getId());
            assertThat(linkedToOrder.statusCode()).isEqualTo(200);
            assertThat(linkedToOrder.jsonPath().getList("productionOrderId", Long.class))
                    .contains(productionOrder.getId());

            Response unlinked = productionOrderFixture.unlinkOrderRaw(
                    ORDER_ADMIN, productionOrder.getId(), order.getId());
            assertThat(unlinked.statusCode()).isIn(200, 204);
            assertThat(productionOrderFixture.getLinkedToOrderRaw(ORDER_ADMIN, order.getId())
                    .jsonPath().getList("productionOrderId", Long.class))
                    .doesNotContain(productionOrder.getId());
        } finally {
            productionOrderFixture.cancel(UserRole.ADMIN, productionOrder.getId());
        }
    }

    private OrderRelocationTaskResponse findTask(long orderId, long taskId) {
        return relocationTaskFixture.getForOrder(ORDER_ADMIN, orderId).stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Relocation task " + taskId + " not returned for order " + orderId));
    }

    private void resetIsolatedGatheringStock() {
        Long storageId = isolatedGatheringStorage.getId();
        Double booked = readBookedAmount(storageId);
        if (booked != null && booked >= 0.01) {
            throw new AssertionError(
                    "Isolated gathering " + storageId + " still has bookedAmount=" + booked);
        }
        inventoryFixture.resetResourceStock(storageId, resourceId, 0.0, UserRole.ADMIN);
    }
}
