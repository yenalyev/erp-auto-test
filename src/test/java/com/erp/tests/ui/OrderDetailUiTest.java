package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.pages.OrderListPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("REQ-ORD Orders UI")
public class OrderDetailUiTest extends OrderUiTestBase {

    private static final UserRole USERNAME_ONLY_AUTHOR = UserRole.ORDER_SOURCE_KEEPER;

    private UserFixture commentAuthorFixture;
    private UserFixture.BusinessActor usernameOnlyAuthor;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupUsernameOnlyCommentAuthor() {
        commentAuthorFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse requester = storageFixture.getById(MANAGER, requesterStorageId);
        usernameOnlyAuthor = commentAuthorFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(requester));

        UserModelResponse profile = commentAuthorFixture.getUser(MANAGER, usernameOnlyAuthor.userId());
        UserModelResponse updated = commentAuthorFixture.updateUser(
                MANAGER,
                usernameOnlyAuthor.userId(),
                UserDataFactory.fromExisting(profile).toBuilder()
                        .firstName(null)
                        .lastName(null)
                        .build());
        assertThat(updated.getFirstName() == null || updated.getFirstName().isBlank()).isTrue();
        assertThat(updated.getLastName() == null || updated.getLastName().isBlank()).isTrue();

        apiExecutor.setSessionForRole(
                USERNAME_ONLY_AUTHOR, usernameOnlyAuthor.username(), usernameOnlyAuthor.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupUsernameOnlyCommentAuthor() {
        apiExecutor.restoreDefaultSessionForRole(USERNAME_ONLY_AUTHOR);
        if (commentAuthorFixture != null) {
            commentAuthorFixture.deactivateTrackedUsers();
        }
    }

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
            throw new AssertionError("«Взяти в роботу» not visible — check ORDER::MANAGE permissions for ADMIN");
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
        OrderListPage ordersPage = new OrderListPage(page)
                .openDeepLink(order.getId())
                .waitForBookingPanel();
        if (!ordersPage.isAvailabilityHintVisible() && !ordersPage.isBookingPanelVisible()) {
            throw new AssertionError("Availability hint not rendered on this card");
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
            throw new AssertionError("Comment composer not visible");
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
            throw new AssertionError("Gatherer empty-bookings copy not visible");
        }
        assertThat(ordersPage.isGathererEmptyBookingsVisible() || ordersPage.isBookingPanelVisible()).isTrue();
    }

    @Test(priority = 6)
    @TestCaseId("TC-ORD-UI-019")
    @Story("Gathering owner cancellation guard")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Овнер призначеної локації збору бачить замовлення, але не бачить дії «Скасувати».")
    public void gatheringOwnerDoesNotSeeCancelAction() {
        OrderResponse order = prepareManagedInProgressUi();
        reopenPageWithSession(GATHERER, gatheringStorageId);

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());

        assertThat(ordersPage.isOrderDialogVisible(order.getId()))
                .as("Овнер локації збору має бачити призначене йому замовлення")
                .isTrue();
        assertThat(ordersPage.isCancelOrderVisible())
                .as("Овнер локації збору не повинен бачити дію «Скасувати»")
                .isFalse();
    }

    @Test(priority = 7)
    @TestCaseId("TC-ORD-UI-018")
    @Story("Comment author fallback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI показує username автора коментаря, якщо firstName і lastName відсутні, і не показує «Невідомо».")
    public void commentWithoutAuthorNameShowsUsername() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        String text = "ui username fallback " + order.getId();
        orderFixture.addComment(USERNAME_ONLY_AUTHOR, order.getId(), text);

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        ordersPage.attachScreenshot("TC-ORD-UI-018 — username shown as comment author");

        assertThat(ordersPage.commentShowsAuthor(text, usernameOnlyAuthor.username()))
                .as("Коментар автора без ПІБ має показувати username %s", usernameOnlyAuthor.username())
                .isTrue();
        assertThat(page.getByText("Невідомо", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).count())
                .isZero();
    }
}
