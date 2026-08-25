package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.BookingState;
import com.erp.enums.OrderState;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order lifecycle")
public class OrderLifecycleApiTest extends OrderApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-ORD-020")
    @Story("Admin take to work")
    @Severity(SeverityLevel.CRITICAL)
    public void testAdminTakeToWorkNewToInProgress() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        OrderResponse inProgress = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);

        assertThat(inProgress.getState()).isEqualTo(OrderState.IN_PROGRESS);
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-021")
    @Story("Admin mark done")
    public void testAdminMarkDoneWithoutBookings() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        order = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);

        OrderResponse done = orderFixture.markDone(MANAGER, order.getId(), requesterStorageId);

        assertThat(done.getState()).isEqualTo(OrderState.DONE);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-022")
    @Story("Admin mark done")
    @Severity(SeverityLevel.CRITICAL)
    public void testMarkDoneWithActiveBookingReturns400() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_MARK_DONE,
                MANAGER,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);

        OrderResponse unchanged = orderFixture.getById(REQUESTER, order.getId());
        assertThat(unchanged.getState()).isEqualTo(OrderState.IN_PROGRESS);
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-023")
    @Story("Owner cancel")
    public void testOwnerCancelNewOrder() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        OrderResponse cancelled = orderFixture.cancel(REQUESTER, order.getId(), requesterStorageId);

        assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-024")
    @Story("Owner cancel")
    @Severity(SeverityLevel.CRITICAL)
    public void testCancelInProgressReleasesBookings() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        OrderResponse cancelled = orderFixture.cancel(REQUESTER, order.getId(), requesterStorageId);
        assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);

        List<BookingResponse> bookings = orderFixture.getBookings(MANAGER, order.getId());
        assertThat(bookings).isNotEmpty();
        assertThat(bookings.getFirst().getState()).isEqualTo(BookingState.RELEASED);
    }

    @Test(priority = 15)
    @TestCaseId("TC-ORD-026")
    @Story("Illegal transitions")
    public void testAdminMarkDoneFromNewReturns400() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_MARK_DONE,
                MANAGER,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(response.statusCode()).isEqualTo(400);

        OrderResponse unchanged = orderFixture.getById(REQUESTER, order.getId());
        assertThat(unchanged.getState()).isEqualTo(OrderState.NEW);
    }

    @Test(priority = 16)
    @TestCaseId("TC-ORD-027")
    @Story("Owner without manage")
    @Severity(SeverityLevel.CRITICAL)
    public void testOwnerTakeToWorkForbiddenAdminSucceeds() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        Response ownerDenied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_TAKE_TO_WORK,
                REQUESTER,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(ownerDenied.statusCode()).isEqualTo(403);

        OrderResponse inProgress = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);
        assertThat(inProgress.getState()).isEqualTo(OrderState.IN_PROGRESS);
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-025")
    @Story("Owner cancel without manage")
    @Severity(SeverityLevel.CRITICAL)
    @Description("cancel дозволений з order::update (без manage) — REQUESTER скасовує NEW.")
    public void testCancelAllowedWithUpdateWithoutManage() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        OrderResponse cancelled = orderFixture.cancel(REQUESTER, order.getId(), requesterStorageId);
        assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);
    }
}
