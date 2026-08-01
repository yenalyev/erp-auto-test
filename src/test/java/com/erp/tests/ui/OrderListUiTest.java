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
@Feature("REQ-WMS-010 Orders UI")
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
}
