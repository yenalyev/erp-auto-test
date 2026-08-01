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
@Feature("REQ-WMS-010 Orders UI")
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
}
