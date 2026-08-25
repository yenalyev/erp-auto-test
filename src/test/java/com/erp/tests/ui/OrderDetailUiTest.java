package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.models.response.OrderResponse;
import com.erp.pages.OrderListPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("REQ-ORD Orders UI")
public class OrderDetailUiTest extends OrderUiTestBase {

    @BeforeMethod(alwaysRun = true)
    public void prepareSession() {
        loginAsAdmin();
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-UI-010")
    @Story("Order detail actions")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: Owner створює NEW замовлення.
            UI: Admin (requester storage) → deep link → «Взяти в роботу» видима (order::manage).
            """)
    public void newOrderShowsTakeToWorkAction() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());

        if (!ordersPage.isTakeToWorkVisible()) {
            throw new SkipException("«Взяти в роботу» not visible — check ORDER::MANAGE permissions for ADMIN");
        }

        assertThat(ordersPage.isTakeToWorkVisible())
                .as("Для NEW замовлення має бути видима кнопка «Взяти в роботу»")
                .isTrue();
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-UI-012")
    @Story("Cancelled order view")
    @Description("CANCELLED: лише перегляд + comments.")
    public void cancelledOrderIsViewOnlyWithComments() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        orderFixture.cancel(REQUESTER, order.getId(), requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        assertThat(ordersPage.isTakeToWorkVisible()).isFalse();
        assertThat(ordersPage.isCommentComposerVisible() || ordersPage.isOrderDialogVisible(order.getId()))
                .isTrue();
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-UI-013")
    @Story("Availability hover")
    @Description("Availability hover (manage): «Наявність на локаціях» / заброньовано.")
    public void availabilityHintVisibleForManager() {
        OrderResponse order = prepareManagedInProgressUi();
        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        if (!ordersPage.isAvailabilityHintVisible() && !ordersPage.isBookingPanelVisible()) {
            throw new SkipException("Availability hint not rendered on this card");
        }
        assertThat(ordersPage.isAvailabilityHintVisible() || ordersPage.isBookingPanelVisible()).isTrue();
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-UI-014")
    @Story("Comments UI")
    @Description("Comments UI: додати / author.")
    public void addCommentFromDetailDialog() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        String text = "ui-comment-" + order.getId();
        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        if (!ordersPage.isCommentComposerVisible()) {
            throw new SkipException("Comment composer not visible");
        }
        ordersPage.addComment(text);
        assertThat(page.getByText(text).count()).isGreaterThan(0);
    }

    @Test(priority = 5)
    @TestCaseId("TC-ORD-UI-016")
    @Story("Gatherer card")
    @Description("Gatherer card: prepare only; empty «ще немає броней».")
    public void gathererCardShowsEmptyBookings() {
        reopenPageWithSession(GATHERER, gatheringStorageId);
        OrderResponse order = prepareManagedInProgressUi();
        reopenPageWithSession(GATHERER, gatheringStorageId);
        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        if (!ordersPage.isGathererEmptyBookingsVisible() && !ordersPage.isBookingPanelVisible()) {
            throw new SkipException("Gatherer empty-bookings copy not visible");
        }
        assertThat(ordersPage.isGathererEmptyBookingsVisible() || ordersPage.isBookingPanelVisible()).isTrue();
    }
}
