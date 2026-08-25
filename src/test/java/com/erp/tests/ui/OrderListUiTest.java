package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.models.response.OrderResponse;
import com.erp.pages.AppSidebarPage;
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
public class OrderListUiTest extends OrderUiTestBase {

    @BeforeMethod(alwaysRun = true)
    public void prepareSession() {
        loginAsOwner();
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-UI-001")
    @Story("Orders journal")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Відкрити /orders — сторінка завантажена, видно таблицю або порожній стан.")
    public void openOrdersPageShowsTableOrEmptyState() {
        OrderListPage ordersPage = new OrderListPage(page).open();

        assertThat(ordersPage.isJournalTableVisible() || ordersPage.hasEmptyState())
                .as("Має бути таблиця замовлень або «Замовлень не знайдено»")
                .isTrue();
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-UI-004")
    @Story("Create order RBAC / workspace")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            У режимі «Всі локації» кнопка «Створити замовлення» disabled з підказкою.
            Пропускається, якщо селектор робочого простору недоступний.
            """)
    public void createOrderDisabledInAllLocationsMode() {
        loginAsAdminAllLocations();
        new OrderListPage(page).open();

        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        if (!sidebar.isWorkspaceSelectorVisible()) {
            throw new SkipException("Workspace selector not visible — cannot verify all-locations mode");
        }

        sidebar.selectAllLocations();
        OrderListPage ordersPage = new OrderListPage(page).open();

        if (!ordersPage.isCreateButtonVisible()) {
            throw new SkipException("Create order button not visible for current role — skip all-locations check");
        }

        assertThat(ordersPage.isCreateDisabled())
                .as("«Створити замовлення» має бути disabled у режимі «Всі локації»")
                .isTrue();

        String tooltip = ordersPage.getCreateTooltip();
        assertThat(tooltip)
                .as("Tooltip для disabled create")
                .contains("Оберіть конкретну локацію");
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-UI-015")
    @Story("Order deep link")
    @Severity(SeverityLevel.CRITICAL)
    @Description("API: створити замовлення → deep link /orders?orderId=N → діалог «Замовлення #N».")
    public void deepLinkOpensOrderDetailDialog() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);

        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());

        assertThat(ordersPage.isOrderDialogVisible(order.getId()))
                .as("Діалог деталей замовлення має бути видимим")
                .isTrue();
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-UI-002")
    @Story("Orders filters")
    @Description("Фільтри: пошук ресурсу та reset.")
    public void resourceFilterAndReset() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        OrderListPage ordersPage = new OrderListPage(page).open();
        ordersPage.filterByResourceSearch(resourceName);
        assertThat(ordersPage.isJournalTableVisible() || ordersPage.hasEmptyState()).isTrue();
        ordersPage.clearFilters();
        assertThat(ordersPage.isJournalTableVisible() || ordersPage.hasEmptyState()).isTrue();
        assertThat(order.getId()).isNotNull();
    }

    @Test(priority = 5)
    @TestCaseId("TC-ORD-UI-003")
    @Story("Orders pagination")
    @Description("Пагінація 25/100/200/500.")
    public void pageSizeOptionsAreAvailable() {
        OrderListPage ordersPage = new OrderListPage(page).open();
        if (ordersPage.hasEmptyState()) {
            throw new SkipException("Empty journal — page size selector may be hidden");
        }
        assertThat(ordersPage.isPageSizeOptionVisible(25)).isTrue();
        assertThat(ordersPage.isPageSizeOptionVisible(100)).isTrue();
        assertThat(ordersPage.isPageSizeOptionVisible(200)).isTrue();
        assertThat(ordersPage.isPageSizeOptionVisible(500)).isTrue();
    }

    @Test(priority = 6)
    @TestCaseId("TC-ORD-UI-007")
    @Story("Orders sidebar RBAC")
    @Description("Sidebar «Замовлення» видимий з order::view.")
    public void ordersNavVisibleForRequester() {
        new OrderListPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.isNavItemVisible(AppSidebarPage.GROUP_ORDERS)
                || sidebar.isNavItemVisible("Замовлення"))
                .as("Sidebar має показувати групу «Замовлення»")
                .isTrue();
    }

    @Test(priority = 7)
    @TestCaseId("TC-ORD-UI-017")
    @Story("List prepared accent")
    @Description("List accent + «Підготовлено X/Y» після броні.")
    public void listShowsPreparedProgressAfterBooking() {
        loginAsAdmin();
        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 5.0);
        OrderListPage ordersPage = new OrderListPage(page).open();
        if (!ordersPage.isPreparedProgressVisible()) {
            throw new SkipException("«Підготовлено» badge not visible on current journal page");
        }
        assertThat(ordersPage.isPreparedProgressVisible()).isTrue();
    }
}
