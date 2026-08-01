package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.models.response.OrderResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-WMS-010 Order RBAC")
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
}
