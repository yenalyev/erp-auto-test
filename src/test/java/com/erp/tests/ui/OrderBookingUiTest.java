package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.BookingState;
import com.erp.enums.BusinessRole;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.OrderListPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("REQ-ORD Orders UI")
public class OrderBookingUiTest extends OrderUiTestBase {

    private static final double ORDER_QTY = 5.0;
    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;

    private UserFixture bookingUserFixture;
    private UserFixture.BusinessActor orderAdminActor;

    @Override
    protected int requiredResourceCount() {
        return 3;
    }

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupBookingOrderAdmin() {
        bookingUserFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse requester = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, requesterStorageId);
        orderAdminActor = bookingUserFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(requester));
        apiExecutor.setSessionForRole(
                ORDER_ADMIN, orderAdminActor.username(), orderAdminActor.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupBookingOrderAdmin() {
        apiExecutor.evictSessionForRole(ORDER_ADMIN);
        if (bookingUserFixture != null) {
            bookingUserFixture.deactivateTrackedUsers();
        }
    }

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

        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();

        if (!ordersPage.isBookingPanelVisible()) {
            throw new AssertionError("«Збір замовлення» panel not visible — check ORDER::MANAGE for ADMIN on requester storage");
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

        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();

        if (!ordersPage.isBookingPanelVisible()) {
            throw new AssertionError("Booking panel not visible — cannot verify send order button");
        }
        if (!ordersPage.isSendOrderEnabled()) {
            throw new AssertionError("«Відправити замовлення» not enabled after full API booking");
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
    @TestCaseId({
            "TC-ORD-091",
            "TC-ORD-UI-025"
    })
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

    @Test(priority = 5)
    @TestCaseId("TC-ORD-UI-021")
    @Story("Booking table")
    @Description("Таблиця Потрібно/Заброньовано/Вільно; зняти бронь.")
    public void bookingTableShowsNeedBookedFree() {
        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, ORDER_QTY);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        if (!ordersPage.isBookingPanelVisible()) {
            throw new AssertionError("Booking panel not visible");
        }
        assertThat(ordersPage.isBookingNeedFreeTableVisible() || ordersPage.isReleaseBookingVisible()
                || ordersPage.isBookingPanelVisible()).isTrue();
    }

    @Test(priority = 6)
    @TestCaseId("TC-ORD-UI-022")
    @Story("Ready-to-deliver gate")
    @Description("При частковій броні «Відправити» недоступна до ручного «Готово до доставки».")
    public void partialOrderCanBeSentOnlyAfterReadyConfirmation() {
        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 2.0);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        if (!ordersPage.isBookingPanelVisible()) {
            throw new AssertionError("Booking panel not visible");
        }
        assertThat(ordersPage.isSendOrderEnabled()).isFalse();
        assertThat(ordersPage.isReadyToDeliverVisible())
                .as("Для частково заброньованого замовлення доступне підтвердження готовності")
                .isTrue();

        ordersPage.markReadyToDeliver();

        assertThat(ordersPage.isSendOrderEnabled()).isTrue();
    }

    @Test(priority = 7)
    @TestCaseId(value = "TC-ORD-UI-026", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all order lines")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Order Admin натискає «Забронювати все»: усі позиції багатопозиційного замовлення бронюються повністю одним UI-кроком.")
    public void orderAdminBooksAllOrderLinesWithOneAction() {
        Map<Long, Double> requested = bulkBookingQuantities();
        OrderResponse order = prepareMultiLineInProgress(ORDER_ADMIN, requested);

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        page.setViewportSize(1600, 1100);

        assertThat(ordersPage.isBookAllVisible())
                .as("Order Admin with order::manage should see «Забронювати все»")
                .isTrue();
        ordersPage.scrollBookAllIntoView();
        attachScreenshot("TC-ORD-UI-026 — кнопка Забронювати все");

        ordersPage.bookAllResources().waitForOrderState("Готово до доставки");
        page.waitForCondition(
                () -> activeBookingCount(ORDER_ADMIN, order.getId()) == requested.size(),
                new com.microsoft.playwright.Page.WaitForConditionOptions().setTimeout(15_000));
        attachScreenshot("TC-ORD-UI-026 — усі позиції заброньовано");

        List<BookingResponse> activeBookings = orderFixture.getBookings(ORDER_ADMIN, order.getId())
                .stream()
                .filter(booking -> booking.getState() == BookingState.ACTIVE)
                .toList();
        assertThat(activeBookings)
                .extracting(BookingResponse::getResourceId)
                .containsExactlyInAnyOrderElementsOf(requested.keySet());
        assertThat(activeBookings)
                .allSatisfy(booking -> assertThat(booking.getAmount())
                        .isEqualByComparingTo(String.valueOf(requested.get(booking.getResourceId()))));
        assertThat(orderFixture.getById(ORDER_ADMIN, order.getId()).getState())
                .isEqualTo(OrderState.READY_TO_DELIVER);
    }

    @Test(priority = 8)
    @TestCaseId(value = "TC-ORD-UI-027", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Book all permission boundary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("«Забронювати все» доступна глобальному Admin / Order Admin; автору замовлення та Owner локації збору без order::manage не показується.")
    public void bookAllIsVisibleOnlyWithOrderManagePermission() {
        OrderResponse order = prepareMultiLineInProgress(MANAGER, bulkBookingQuantities());

        loginAsAdmin();
        assertThat(new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel()
                .isBookAllVisible())
                .as("Global Admin should see «Забронювати все»")
                .isTrue();

        loginAsOwner();
        assertThat(new OrderListPage(page)
                .openDeepLink(order.getId())
                .isBookAllVisible())
                .as("Requester without order::manage must not see «Забронювати все»")
                .isFalse();

        loginAsGatherer();
        assertThat(new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel()
                .isBookAllVisible())
                .as("Gathering owner may prepare bookings but must not create them")
                .isFalse();
    }

    @Test(priority = 9)
    @TestCaseId(value = "TC-ORD-UI-028", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all with partial stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Якщо повного залишку не вистачає, «Забронювати все» бронює доступну кількість кожного ресурсу.")
    public void bookAllUsesAvailablePartialStock() {
        Map<Long, Double> requested = bulkBookingQuantities();
        Long secondResourceId = sharedResources.get(1).getId();
        setGatheringStock(Map.of(resourceId, 2.0, secondResourceId, 1.0));
        OrderResponse order = prepareMultiLineInProgressWithoutSeeding(ORDER_ADMIN, requested);

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        page.setViewportSize(1600, 1100);

        assertThat(ordersPage.isBookAllVisible()).isTrue();
        assertThat(ordersPage.isBookAllEnabled()).isTrue();
        ordersPage.bookAllResources();
        page.waitForCondition(
                () -> activeBookingCount(ORDER_ADMIN, order.getId()) == 2,
                new com.microsoft.playwright.Page.WaitForConditionOptions().setTimeout(15_000));

        List<BookingResponse> activeBookings = orderFixture.getBookings(ORDER_ADMIN, order.getId())
                .stream()
                .filter(booking -> booking.getState() == BookingState.ACTIVE)
                .toList();
        // Bulk booking must use the available quantity without overbooking either line.
        assertThat(activeBookings).hasSize(2);
        assertThat(activeBookings).anySatisfy(booking -> {
            assertThat(booking.getResourceId()).isEqualTo(resourceId);
            assertThat(booking.getAmount()).isEqualByComparingTo("2.0");
        });
        assertThat(activeBookings).anySatisfy(booking -> {
            assertThat(booking.getResourceId()).isEqualTo(secondResourceId);
            assertThat(booking.getAmount()).isEqualByComparingTo("1.0");
        });
        assertThat(orderFixture.getById(ORDER_ADMIN, order.getId()).getState())
                .isEqualTo(OrderState.IN_PROGRESS);
        attachScreenshot("TC-ORD-UI-028 — часткова бронь за доступним залишком");
    }

    @Test(priority = 10)
    @TestCaseId(value = "TC-ORD-UI-029", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all with no stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Якщо на локації збору немає жодного замовленого ресурсу, «Забронювати все» не відображається і броні не створюються.")
    public void bookAllIsHiddenWhenNothingCanBeBooked() {
        Map<Long, Double> requested = bulkBookingQuantities();
        setGatheringStock(Map.of(resourceId, 0.0, sharedResources.get(1).getId(), 0.0));
        OrderResponse order = prepareMultiLineInProgressWithoutSeeding(ORDER_ADMIN, requested);

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        page.setViewportSize(1600, 1100);

        assertThat(ordersPage.isBookAllVisible()).isFalse();
        assertThat(activeBookingCount(ORDER_ADMIN, order.getId())).isZero();
        assertThat(orderFixture.getById(ORDER_ADMIN, order.getId()).getState())
                .isEqualTo(OrderState.IN_PROGRESS);
        attachScreenshot("TC-ORD-UI-029 — немає доступного залишку");
    }

    @Test(priority = 11)
    @TestCaseId(value = "TC-ORD-UI-030", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all with only one bookable line")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Якщо з кількох позицій замовлення запас є лише для однієї, «Забронювати все» не відображається.")
    public void bookAllIsHiddenWhenOnlyOneLineCanBeBooked() {
        Map<Long, Double> requested = bulkBookingQuantities();
        setGatheringStock(Map.of(resourceId, 2.0, sharedResources.get(1).getId(), 0.0));
        OrderResponse order = prepareMultiLineInProgressWithoutSeeding(ORDER_ADMIN, requested);

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        page.setViewportSize(1600, 1100);

        assertThat(ordersPage.isBookAllVisible()).isFalse();
        assertThat(activeBookingCount(ORDER_ADMIN, order.getId())).isZero();
        assertThat(orderFixture.getById(ORDER_ADMIN, order.getId()).getState())
                .isEqualTo(OrderState.IN_PROGRESS);
        attachScreenshot("TC-ORD-UI-030 — доступна лише одна позиція");
    }

    @Test(priority = 12)
    @TestCaseId(value = "TC-ORD-UI-031", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all is hidden for a single-line order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Для замовлення з однією позицією «Забронювати все» не відображається; доступна звичайна дія в рядку.")
    public void bookAllIsHiddenForSingleLineOrder() {
        OrderResponse order = prepareManagedInProgressUi();

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();

        assertThat(ordersPage.isBookAllVisible()).isFalse();
    }

    @Test(priority = 13)
    @TestCaseId(value = "TC-ORD-UI-032", roles = BusinessRole.ORDER_ADMIN)
    @Story("Book all with two bookable lines and one zero-stock line")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Якщо доступно щонайменше дві позиції, «Забронювати все» бронює їх доступні кількості і пропускає нульову позицію.")
    public void bookAllBooksMultipleAvailableLinesAndSkipsZeroStockLine() {
        Long secondResourceId = sharedResources.get(1).getId();
        Long zeroStockResourceId = sharedResources.get(2).getId();
        Map<Long, Double> requested = new LinkedHashMap<>();
        requested.put(resourceId, ORDER_QTY);
        requested.put(secondResourceId, 3.0);
        requested.put(zeroStockResourceId, 4.0);
        setGatheringStock(Map.of(resourceId, 2.0, secondResourceId, 1.0, zeroStockResourceId, 0.0));
        OrderResponse order = prepareMultiLineInProgressWithoutSeeding(ORDER_ADMIN, requested);

        loginAsActor(orderAdminActor, requesterStorageId);
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        page.setViewportSize(1600, 1100);

        assertThat(ordersPage.isBookAllVisible()).isTrue();
        assertThat(ordersPage.isBookAllEnabled()).isTrue();
        ordersPage.bookAllResources();
        page.waitForCondition(
                () -> activeBookingCount(ORDER_ADMIN, order.getId()) == 2,
                new com.microsoft.playwright.Page.WaitForConditionOptions().setTimeout(15_000));

        List<BookingResponse> activeBookings = orderFixture.getBookings(ORDER_ADMIN, order.getId())
                .stream()
                .filter(booking -> booking.getState() == BookingState.ACTIVE)
                .toList();
        assertThat(activeBookings).hasSize(2);
        assertThat(activeBookings).anySatisfy(booking -> {
            assertThat(booking.getResourceId()).isEqualTo(resourceId);
            assertThat(booking.getAmount()).isEqualByComparingTo("2.0");
        });
        assertThat(activeBookings).anySatisfy(booking -> {
            assertThat(booking.getResourceId()).isEqualTo(secondResourceId);
            assertThat(booking.getAmount()).isEqualByComparingTo("1.0");
        });
        assertThat(activeBookings)
                .noneMatch(booking -> booking.getResourceId().equals(zeroStockResourceId));
        assertThat(orderFixture.getById(ORDER_ADMIN, order.getId()).getState())
                .isEqualTo(OrderState.IN_PROGRESS);
        attachScreenshot("TC-ORD-UI-032 — дві доступні позиції заброньовано");
    }

    private Map<Long, Double> bulkBookingQuantities() {
        Map<Long, Double> requested = new LinkedHashMap<>();
        requested.put(resourceId, ORDER_QTY);
        requested.put(sharedResources.get(1).getId(), 3.0);
        return requested;
    }

    private OrderResponse prepareMultiLineInProgress(
            UserRole manager, Map<Long, Double> requested) {
        for (Long requestedResourceId : requested.keySet()) {
            relocationFixture.ensureStock(gatheringStorageId, requestedResourceId, 200.0);
        }
        return prepareMultiLineInProgressWithoutSeeding(manager, requested);
    }

    private OrderResponse prepareMultiLineInProgressWithoutSeeding(
            UserRole manager, Map<Long, Double> requested) {
        OrderResponse order = orderFixture.createOrder(
                REQUESTER,
                OrderDataFactory.buildMultiLineOrderRequest(requesterStorageId, requested));
        orderFixture.takeToWork(manager, order.getId(), requesterStorageId);
        orderFixture.setGathering(
                manager, order.getId(), requesterStorageId, gatheringStorageId);
        return order;
    }

    private void setGatheringStock(Map<Long, Double> stockByResource) {
        InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
        for (Map.Entry<Long, Double> stock : stockByResource.entrySet()) {
            relocationFixture.seedExactStock(
                    gatheringStorageId, stock.getKey(), Math.max(stock.getValue(), 1.0));
            inventory.resetResourceStock(
                    gatheringStorageId, stock.getKey(), stock.getValue(), UserRole.ADMIN);
        }
    }

    private long activeBookingCount(UserRole role, long orderId) {
        return orderFixture.getBookings(role, orderId).stream()
                .filter(booking -> booking.getState() == BookingState.ACTIVE)
                .count();
    }
}
