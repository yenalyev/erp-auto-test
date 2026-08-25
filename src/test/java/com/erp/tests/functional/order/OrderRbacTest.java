package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order RBAC")
public class OrderRbacTest extends OrderApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-ORD-RBAC-001")
    @Story("Anonymous access")
    @Severity(SeverityLevel.CRITICAL)
    public void testAnonymousCreateOrderDenied() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_CREATE,
                UserRole.ANONYMOUS,
                OrderDataFactory.buildOrderRequest(requesterStorageId, resourceId, DEFAULT_ORDER_QTY));
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-RBAC-003")
    @Story("Admin manage vs Owner")
    @Severity(SeverityLevel.CRITICAL)
    public void testOwnerCannotTakeToWorkAdminCan() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        Response ownerDenied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_TAKE_TO_WORK,
                REQUESTER,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(ownerDenied.statusCode()).isEqualTo(403);

        OrderResponse managed = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);
        assertThat(managed.getState()).isEqualTo(OrderState.IN_PROGRESS);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-076")
    @Story("Book requires manage")
    @Severity(SeverityLevel.CRITICAL)
    public void testOwnerCannotBookAdminCan() {
        OrderResponse order = prepareManagedInProgress();

        Response ownerDenied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                REQUESTER,
                OrderDataFactory.buildBookingRequest(resourceId, DEFAULT_ORDER_QTY),
                order.getId(),
                requesterStorageId);
        assertThat(ownerDenied.statusCode()).isEqualTo(403);

        var booking = orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        assertThat(booking.getId()).isNotNull();
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-RBAC-002")
    @Story("Owner update lines only when NEW")
    public void testOwnerUpdateOnlyWhenNew() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        OrderResponse updated = orderFixture.updateOrder(
                REQUESTER,
                order.getId(),
                OrderDataFactory.buildOrderRequest(requesterStorageId, resourceId, 3.0));
        assertThat(updated.getLines().getFirst().getQuantity().doubleValue()).isEqualTo(3.0);

        orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);
        Response denied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_UPDATE,
                REQUESTER,
                OrderDataFactory.buildOrderRequest(requesterStorageId, resourceId, 2.0),
                order.getId());
        assertThat(denied.statusCode()).isEqualTo(400);
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-RBAC-004")
    @Story("Gathering read without update")
    @Description("Gathering read: list+get+bookings view; без update — немає prepare.")
    public void testRequesterCannotPrepareWithoutGatheringUpdate() {
        OrderResponse order = prepareManagedInProgress();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        OrderResponse fetched = orderFixture.getById(REQUESTER, order.getId());
        assertThat(fetched.getId()).isEqualTo(order.getId());
        Response denied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_BOOKING_PREPARED,
                REQUESTER,
                OrderDataFactory.buildPreparedRequest(true),
                order.getId(),
                booking.getId());
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    @Test(priority = 15)
    @TestCaseId("TC-ORD-RBAC-005")
    @Story("Gathering update without manage")
    @Description("Gathering update: prepare; без manage — немає book/send.")
    public void testGathererCanPrepareButCannotBookOrSend() {
        OrderResponse order = prepareManagedInProgress();
        Response bookDenied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                GATHERER,
                OrderDataFactory.buildBookingRequest(resourceId, DEFAULT_ORDER_QTY),
                order.getId(),
                requesterStorageId);
        assertThat(bookDenied.statusCode()).isEqualTo(403);

        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        BookingResponse prepared = orderFixture.setPrepared(
                GATHERER, order.getId(), booking.getId(), true);
        assertThat(prepared.isPrepared()).isTrue();

        RelocationOutputRequest send = OrderDataFactory.buildShipRequest(
                order.getId(), gatheringStorageId, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        Response sendDenied = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);
        assertThat(sendDenied.statusCode()).isIn(400, 403);
    }
}
