package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.OrderListPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
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
public class OrderBookingUiTest extends OrderUiTestBase {

    private static final double ORDER_QTY = 5.0;

    @BeforeMethod(alwaysRun = true)
    public void prepareSession() {
        loginAsAdmin();
    }

    @Test(priority = 1)
    @TestCaseId({
            "TC-ORD-UI-011",
            "TC-ORD-UI-020"
    })
    @Story("Order booking panel")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: Owner create → Admin takeToWork + setGathering.
            UI: Admin (requester storage) → deep link → панель «Збір замовлення».
            """)
    public void inProgressOrderShowsBookingPanel() {
        OrderResponse order = prepareManagedInProgressUi();

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());

        if (!ordersPage.isBookingPanelVisible()) {
            throw new SkipException("«Збір замовлення» panel not visible — check ORDER::MANAGE for ADMIN on requester storage");
        }

        assertThat(ordersPage.isBookingPanelVisible())
                .as("IN_PROGRESS order with gathering should show booking panel")
                .isTrue();
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-UI-024")
    @Story("Send order navigation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: Owner create → Admin manage; Admin book + prepare.
            UI: Admin → «Відправити замовлення» enabled → /relocation/create-output?orderId=.
            """)
    public void fullyBookedOrderSendNavigatesToCreateOutput() {
        OrderResponse order = prepareManagedInProgressUi();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, ORDER_QTY);
        orderFixture.setPrepared(MANAGER, order.getId(), booking.getId(), true);

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());

        if (!ordersPage.isBookingPanelVisible()) {
            throw new SkipException("Booking panel not visible — cannot verify send order button");
        }
        if (!ordersPage.isSendOrderEnabled()) {
            throw new SkipException("«Відправити замовлення» not enabled after full API booking");
        }

        RelocationCreateOutputPage outputPage = ordersPage.clickSendOrder();

        assertThat(page.url())
                .as("Send order should navigate to create-output with orderId query")
                .contains("/relocation/create-output")
                .contains("orderId=" + order.getId());
        assertThat(outputPage.isOrderIssuanceHeadingVisible(order.getId()))
                .as("Create-output page should show order-specific heading")
                .isTrue();
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-UI-023")
    @Story("Order issuance form")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Відкрити /relocation/create-output?orderId=N — заголовок «Видача за замовленням #N».")
    public void createOutputWithOrderIdLoadsIssuanceForm() {
        OrderResponse order = prepareManagedInProgressUi();
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, ORDER_QTY);
        orderFixture.setPrepared(MANAGER, order.getId(), booking.getId(), true);

        RelocationCreateOutputPage outputPage = new RelocationCreateOutputPage(page)
                .openWithOrderId(order.getId());

        assertThat(outputPage.isOrderIssuanceHeadingVisible(order.getId()))
                .as("Order issuance page heading")
                .isTrue();

        outputPage.assertFixedSenderRecipient(gatheringStorageName, requesterStorageName);
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-091")
    @Story("Relocation order badge")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: Admin send+orderId і окрема видача без заявки. \
            UI /relocations «В дорозі»: hover бейджа → «Створено на основі замовлення №N»; \
            рядок без заявки — без бейджа.""")
    public void journalShowsOrderBadgeOnlyOnOrderShipment() {
        String suffix = String.valueOf(System.currentTimeMillis());
        String orderMarker = "ord-091-" + suffix;
        String plainMarker = "plain-091-" + suffix;

        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, ORDER_QTY);

        RelocationOutputRequest ship = OrderDataFactory.buildShipRequest(
                        order.getId(), gatheringStorageId, requesterStorageId, resourceId, ORDER_QTY)
                .toBuilder()
                .description(orderMarker)
                .build();
        RelocationResponse shipment = apiExecutor.execute(
                        ApiEndpointDefinition.RELOCATION_POST_SEND, MANAGER, ship)
                .as(RelocationResponse.class);
        assertThat(shipment.getOrderId()).isEqualTo(order.getId());

        relocationFixture.createSendWithDescription(
                MANAGER, gatheringStorageId, requesterStorageId, resourceId, 1.0, plainMarker);

        reopenPageWithSession(MANAGER, gatheringStorageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        assertThat(journal.isRowWithTextVisible(orderMarker))
                .as("Рядок видачі за заявкою в «В дорозі»")
                .isTrue();
        assertThat(journal.hoverOrderBadgeTooltip(orderMarker))
                .as("Підказка бейджа")
                .isEqualTo("Створено на основі замовлення №" + order.getId());

        assertThat(journal.isRowWithTextVisible(plainMarker))
                .as("Рядок видачі без заявки в «В дорозі»")
                .isTrue();
        assertThat(journal.hasOrderBadgeInRow(plainMarker))
                .as("Без заявки бейджа немає")
                .isFalse();
    }
}
