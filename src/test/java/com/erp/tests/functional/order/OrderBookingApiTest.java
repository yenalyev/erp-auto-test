package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.BookingState;
import com.erp.enums.OrderState;
import com.erp.enums.RelocationState;
import com.erp.models.request.BookingRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-WMS-010 Order booking and fulfillment")
public class OrderBookingApiTest extends OrderApiTestBase {

    @Test(priority = 5)
    @TestCaseId("TC-ORD-090")
    @Story("Happy path fulfillment")
    @Severity(SeverityLevel.CRITICAL)
    @Description("create → takeToWork → setGathering → book → prepare → shipOrder")
    public void testHappyPathBookingAndShip() {
        double qty = DEFAULT_ORDER_QTY;
        Set<Long> tracked = trackedResource();

        OrderResponse order = prepareManagedInProgress();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, qty);
        orderFixture.setPrepared(MANAGER, order.getId(), booking.getId(), true);

        ProductionStockAssertions.StockSnapshot gatheringBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ДО ship");

        RelocationResponse shipment = orderFixture.shipOrder(
                MANAGER, order.getId(), gatheringStorageId, requesterStorageId, resourceId, qty);

        OrderResponse done = orderFixture.getById(REQUESTER, order.getId());
        assertThat(done.getState()).isEqualTo(OrderState.DONE);
        assertThat(shipment.getOrderId()).isEqualTo(order.getId());

        List<BookingResponse> bookings = orderFixture.getBookings(MANAGER, order.getId());
        assertThat(bookings.stream().anyMatch(b -> b.getState() == BookingState.FULFILLED)).isTrue();

        ProductionStockAssertions.StockSnapshot gatheringAfter = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ПІСЛЯ ship");
        RelocationStockAssertions.assertDebitedFromSender(
                gatheringBefore, gatheringAfter, gatheringStorageId, resourceId, qty, "order ship");
    }

    @Test(priority = 10)
    @TestCaseId("TC-ORD-060")
    @Story("Gathering storage")
    @Severity(SeverityLevel.CRITICAL)
    public void testSetGatheringStorage() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        order = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);
        Long gatheringId = orderFixture.resolveGatheringStorageId(MANAGER, order.getId(), requesterStorageId);
        relocationFixture.ensureStock(gatheringId, resourceId, DEFAULT_SEED_STOCK);

        OrderResponse withGathering = orderFixture.setGathering(
                MANAGER, order.getId(), requesterStorageId, gatheringId);

        assertThat(withGathering.getGatheringStorage()).isNotNull();
        assertThat(withGathering.getGatheringStorage().getId()).isEqualTo(gatheringId);
        gatheringStorageId = gatheringId;
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-063")
    @Story("Gathering storage")
    @Severity(SeverityLevel.CRITICAL)
    public void testChangeGatheringBlockedWithActiveBooking() {
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        Long rootId = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (rootId <= 0) {
            rootId = gatheringStorageId;
        }
        StorageResponse alternateGathering = storageFixture.createChildStorage(rootId, "ord-gath-block-");

        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_GATHERING_STORAGE,
                MANAGER,
                OrderDataFactory.buildGatheringStorageRequest(alternateGathering.getId()),
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-064")
    @Story("Gathering storage")
    public void testChangeGatheringAllowedAfterRelease() {
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        Long rootId = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (rootId <= 0) {
            rootId = gatheringStorageId;
        }
        StorageResponse alternateGathering = storageFixture.createChildStorage(rootId, "ord-gath-change-");
        relocationFixture.ensureStock(alternateGathering.getId(), resourceId, DEFAULT_SEED_STOCK);

        OrderResponse order = prepareManagedInProgress();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        orderFixture.releaseBooking(MANAGER, order.getId(), booking.getId(), requesterStorageId);

        OrderResponse updated = orderFixture.setGathering(
                MANAGER, order.getId(), requesterStorageId, alternateGathering.getId());
        assertThat(updated.getGatheringStorage()).isNotNull();
        assertThat(updated.getGatheringStorage().getId()).isEqualTo(alternateGathering.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-ORD-070")
    @Story("Book resource")
    @Severity(SeverityLevel.CRITICAL)
    public void testBookResourceActive() {
        OrderResponse order = prepareManagedInProgress();

        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getState()).isEqualTo(BookingState.ACTIVE);
        assertThat(booking.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(DEFAULT_ORDER_QTY));
    }

    @Test(priority = 21)
    @TestCaseId("TC-ORD-071")
    @Story("Book resource")
    public void testMergeBookTwiceIntoOneHold() {
        OrderResponse order = prepareManagedInProgress();

        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 2.0);
        BookingResponse merged = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, 3.0);

        assertThat(merged.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(DEFAULT_ORDER_QTY));
        List<BookingResponse> bookings = orderFixture.getBookings(MANAGER, order.getId());
        assertThat(bookings.stream().filter(b -> b.getState() == BookingState.ACTIVE)).hasSize(1);
    }

    @Test(priority = 22)
    @TestCaseId("TC-ORD-072")
    @Story("Book validation")
    @Severity(SeverityLevel.CRITICAL)
    public void testBookInsufficientStockReturns400() {
        OrderResponse order = prepareManagedInProgress();
        resetGatheringOnHandKeepingOrders(3.0);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                MANAGER,
                OrderDataFactory.buildBookingRequest(resourceId, DEFAULT_ORDER_QTY),
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().asString()).containsAnyOf("вільного залишку", "Недостатньо");
    }

    @Test(priority = 23)
    @TestCaseId("TC-ORD-073")
    @Story("Book validation")
    public void testBookExceedsLineQuantityReturns400() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 4.0);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                MANAGER,
                OrderDataFactory.buildBookingRequest(resourceId, 4.0),
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 24)
    @TestCaseId("TC-ORD-074")
    @Story("Book validation")
    public void testBookWithoutGatheringReturns400() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        order = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);

        BookingRequest request = OrderDataFactory.buildBookingRequest(resourceId, DEFAULT_ORDER_QTY);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                MANAGER,
                request,
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 25)
    @TestCaseId("TC-ORD-075")
    @Story("Release booking")
    @Severity(SeverityLevel.CRITICAL)
    public void testReleaseThenBookAgain() {
        OrderResponse order = prepareManagedInProgress();
        BookingResponse first = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        orderFixture.releaseBooking(MANAGER, order.getId(), first.getId(), requesterStorageId);

        BookingResponse second = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, 4.0);
        assertThat(second.getState()).isEqualTo(BookingState.ACTIVE);

        List<BookingResponse> bookings = orderFixture.getBookings(MANAGER, order.getId());
        assertThat(bookings.stream().filter(b -> b.getState() == BookingState.RELEASED)).hasSize(1);
        assertThat(bookings.stream().filter(b -> b.getState() == BookingState.ACTIVE)).hasSize(1);
    }

    @Test(priority = 30)
    @TestCaseId("TC-ORD-080")
    @Story("Prepared flag")
    @Severity(SeverityLevel.CRITICAL)
    public void testSetPreparedOnBooking() {
        OrderResponse order = prepareManagedInProgress();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        BookingResponse prepared = orderFixture.setPrepared(
                MANAGER, order.getId(), booking.getId(), true);

        assertThat(prepared.isPrepared()).isTrue();
        assertThat(prepared.getPreparedAt()).isNotNull();
    }

    @Test(priority = 31)
    @TestCaseId("TC-ORD-081")
    @Story("Prepared flag")
    public void testSetAllPrepared() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        List<BookingResponse> prepared = orderFixture.setAllPrepared(MANAGER, order.getId(), true);

        assertThat(prepared).isNotEmpty();
        assertThat(prepared).allMatch(BookingResponse::isPrepared);
    }

    @Test(priority = 32)
    @TestCaseId("TC-ORD-084")
    @Story("Ship without prepared")
    public void testShipWithoutPreparedAllowed() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        RelocationResponse shipment = orderFixture.shipOrder(
                MANAGER, order.getId(), gatheringStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        assertThat(shipment.getOrderId()).isEqualTo(order.getId());
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.DONE);
    }

    @Test(priority = 40)
    @TestCaseId("TC-ORD-091")
    @Story("Relocation link")
    @Severity(SeverityLevel.CRITICAL)
    public void testShipSetsRelocationOrderId() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        RelocationResponse shipment = orderFixture.shipOrder(
                MANAGER, order.getId(), gatheringStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        assertThat(shipment.getOrderId()).isEqualTo(order.getId());
    }

    @Test(priority = 41)
    @TestCaseId("TC-ORD-093")
    @Story("Ship validation")
    public void testShipUndersendReturns400() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        RelocationOutputRequest undersend = OrderDataFactory.buildShipRequest(
                order.getId(), gatheringStorageId, requesterStorageId, resourceId, 1.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, undersend);
        assertThat(response.statusCode()).isEqualTo(400);

        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.IN_PROGRESS);
    }

    @Test(priority = 42)
    @TestCaseId("TC-ORD-094")
    @Story("Ship validation")
    @Severity(SeverityLevel.CRITICAL)
    public void testShipWrongSenderOrRecipientReturns400() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        RelocationOutputRequest wrongSender = OrderDataFactory.buildShipRequest(
                order.getId(), requesterStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        Response wrongSenderResponse = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, wrongSender);
        assertThat(wrongSenderResponse.statusCode()).isEqualTo(400);

        RelocationOutputRequest wrongRecipient = RelocationOutputRequest.builder()
                .orderId(order.getId())
                .senderId(gatheringStorageId)
                .recipientId(gatheringStorageId)
                .description("wrong-recipient")
                .date(java.time.LocalDate.now())
                .items(List.of(com.erp.models.request.ResourceUsageRequest.builder()
                        .resourceId(resourceId)
                        .amount(BigDecimal.valueOf(DEFAULT_ORDER_QTY))
                        .build()))
                .sendingPersonName("Sender")
                .sendingPersonRank("Rank")
                .receivingPersonName("Receiver")
                .receivingPersonRank("Rank")
                .build();
        Response wrongRecipientResponse = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, wrongRecipient);
        assertThat(wrongRecipientResponse.statusCode()).isEqualTo(400);
    }

    @Test(priority = 43)
    @TestCaseId("TC-ORD-092")
    @Story("Ship overship and extra resources")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin POST /relocations/send + orderId: A більше qty лінії + extra B. \
            Заявка Виконано вже на send; relocation CREATED; зі збору списано. \
            3bat PUT /relocations/{id}/resolve FINISHED («Прийняти» на /relocations) — \
            залишок з’являється на підрозділі. Сусідній C без дельти.""")
    public void testShipOvershipAndExtraResourceAllowed() {
        if (sharedResources == null || sharedResources.size() < 3) {
            throw new SkipException(
                    "Need ≥3 resources in requester UNIT grant; have "
                            + (sharedResources == null ? 0 : sharedResources.size()));
        }
        Long extraResourceId = secondResourceId();
        Long siblingResourceId = sharedResources.get(2).getId();
        double overshipQty = DEFAULT_ORDER_QTY + 3.0;
        double extraQty = 2.0;
        Set<Long> tracked = Set.of(resourceId, extraResourceId, siblingResourceId);

        relocationFixture.ensureStock(gatheringStorageId, extraResourceId, DEFAULT_SEED_STOCK);
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        ProductionStockAssertions.StockSnapshot gatheringBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ДО send");
        ProductionStockAssertions.StockSnapshot requesterBefore = RelocationStockAssertions.capture(
                apiExecutor, requesterStorageId, MANAGER, tracked, "3bat ДО send");

        Map<Long, Double> shipItems = new LinkedHashMap<>();
        shipItems.put(resourceId, overshipQty);
        shipItems.put(extraResourceId, extraQty);
        RelocationOutputRequest request = OrderDataFactory.buildShipMultiItemRequest(
                order.getId(), gatheringStorageId, requesterStorageId, shipItems);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, request);
        assertThat(response.statusCode()).isEqualTo(200);

        RelocationResponse shipment = response.as(RelocationResponse.class);
        assertThat(shipment.getOrderId()).isEqualTo(order.getId());
        assertThat(shipment.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.DONE);
        assertThat(orderFixture.getBookings(MANAGER, order.getId()))
                .anyMatch(booking -> booking.getState() == BookingState.FULFILLED);

        ProductionStockAssertions.StockSnapshot gatheringAfterSend = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ПІСЛЯ send");
        ProductionStockAssertions.StockSnapshot requesterAfterSend = RelocationStockAssertions.capture(
                apiExecutor, requesterStorageId, MANAGER, tracked, "3bat ПІСЛЯ send до Прийняти");
        RelocationStockAssertions.assertStockDelta(
                gatheringBefore, gatheringAfterSend, gatheringStorageId,
                Map.of(resourceId, -overshipQty, extraResourceId, -extraQty, siblingResourceId, 0.0),
                "send: списання зі збору");
        RelocationStockAssertions.assertStockDelta(
                requesterBefore, requesterAfterSend, requesterStorageId,
                Map.of(resourceId, 0.0, extraResourceId, 0.0, siblingResourceId, 0.0),
                "send: на 3bat ще немає — чекаємо Прийняти");

        RelocationResponse received = relocationFixture.resolve(
                REQUESTER, shipment.getId(), requesterStorageId, RelocationState.FINISHED);
        assertThat(received.getState()).isEqualTo(RelocationState.FINISHED);
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.DONE);

        ProductionStockAssertions.StockSnapshot requesterAfterReceive = RelocationStockAssertions.capture(
                apiExecutor, requesterStorageId, MANAGER, tracked, "3bat ПІСЛЯ Прийняти");
        RelocationStockAssertions.assertStockDelta(
                requesterBefore, requesterAfterReceive, requesterStorageId,
                Map.of(resourceId, overshipQty, extraResourceId, extraQty, siblingResourceId, 0.0),
                "Прийняти: зарахування на підрозділ");
    }

    @Test(priority = 44)
    @TestCaseId("TC-ORD-095")
    @Story("Ship rollback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            POST /relocations/send + orderId: items покривають замовлення, плюс зайвий ресурс без залишку \
            на зборі. validateCreateSend не перевіряє on-hand без batches — fulfill() встигає DONE, \
            apply() падає, @Transactional на RelocationFacade відкочує. Очікування: 400, \
            замовлення лишається В роботі, бронь ACTIVE, залишки без змін.""")
    public void testShipFailsAfterFulfillLeavesOrderOpen() {
        if (sharedResources == null || sharedResources.size() < 2) {
            throw new SkipException(
                    "Need ≥2 resources in requester UNIT grant; have "
                            + (sharedResources == null ? 0 : sharedResources.size()));
        }
        Long extraResourceId = secondResourceId();
        Set<Long> tracked = Set.of(resourceId, extraResourceId);

        inventoryFixture.removeResourceFromStorage(gatheringStorageId, extraResourceId, MANAGER);
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        ProductionStockAssertions.StockSnapshot gatheringBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ДО failed ship");

        Map<Long, Double> shipItems = new LinkedHashMap<>();
        shipItems.put(resourceId, DEFAULT_ORDER_QTY);
        shipItems.put(extraResourceId, 1.0);
        RelocationOutputRequest request = OrderDataFactory.buildShipMultiItemRequest(
                order.getId(), gatheringStorageId, requesterStorageId, shipItems);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, request);
        assertThat(response.statusCode()).isEqualTo(400);

        OrderResponse stillOpen = orderFixture.getById(REQUESTER, order.getId());
        assertThat(stillOpen.getState()).isEqualTo(OrderState.IN_PROGRESS);
        assertThat(orderFixture.getBookings(MANAGER, order.getId()))
                .anyMatch(booking -> booking.getState() == BookingState.ACTIVE);
        assertThat(orderFixture.getBookings(MANAGER, order.getId()))
                .noneMatch(booking -> booking.getState() == BookingState.FULFILLED);

        ProductionStockAssertions.StockSnapshot gatheringAfter = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ПІСЛЯ failed ship");
        RelocationStockAssertions.assertUnchanged(
                gatheringBefore, gatheringAfter, gatheringStorageId, resourceId,
                "rollback: замовлений ресурс");
        RelocationStockAssertions.assertUnchanged(
                gatheringBefore, gatheringAfter, gatheringStorageId, extraResourceId,
                "rollback: зайвий ресурс без залишку");
    }

    @Test(priority = 45)
    @TestCaseId("TC-ORD-096")
    @Story("Fulfill RBAC")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Створює підрозділ (3bat). alkatras заявку не бачить. 3bat без manage не відправляє. \
            Admin бере виконання: send+orderId → Виконано.""")
    public void testFulfillDeniedForUnitOwnerAllowedForAdmin() {
        Set<Long> tracked = trackedResource();
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        Response outsiderGet = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_BY_ID, OUTSIDER, null, order.getId());
        assertThat(outsiderGet.statusCode()).isIn(403, 404);

        ProductionStockAssertions.StockSnapshot gatheringBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ДО 3bat ship");

        RelocationOutputRequest ship = OrderDataFactory.buildShipRequest(
                order.getId(), gatheringStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        Response unitOwnerDenied = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, REQUESTER, ship);
        assertThat(unitOwnerDenied.statusCode()).isEqualTo(403);

        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.IN_PROGRESS);
        assertThat(orderFixture.getBookings(MANAGER, order.getId()))
                .anyMatch(booking -> booking.getState() == BookingState.ACTIVE);
        ProductionStockAssertions.StockSnapshot afterDenied = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, MANAGER, tracked, "gathering ПІСЛЯ 3bat 403");
        RelocationStockAssertions.assertUnchanged(
                gatheringBefore, afterDenied, gatheringStorageId, resourceId,
                "підрозділ без manage не списав залишок");

        RelocationResponse shipment = orderFixture.shipOrder(
                MANAGER, order.getId(), gatheringStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        assertThat(shipment.getOrderId()).isEqualTo(order.getId());
        assertThat(orderFixture.getById(REQUESTER, order.getId()).getState()).isEqualTo(OrderState.DONE);
    }
}
