package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
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
public class OrderCreateEditUiTest extends OrderUiTestBase {

    @BeforeMethod(alwaysRun = true)
    public void prepareSession() {
        loginAsOwner();
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-UI-005")
    @Story("Create order dialog")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Натиснути «Створити замовлення» → діалог «Нове замовлення».
            Submit без рядків → валідація «Додайте хоча б один ресурс».
            """)
    public void createDialogShowsValidationOnEmptySubmit() {
        OrderListPage ordersPage = new OrderListPage(page).open();

        if (!ordersPage.isCreateButtonVisible()) {
            throw new SkipException("Create order button not visible for OWNER_1 on requester storage");
        }
        if (ordersPage.isCreateDisabled()) {
            throw new SkipException("Create order button disabled — cannot open create dialog");
        }

        ordersPage.clickCreateOrder();

        ordersPage.submitCreateDialog();

        assertThat(ordersPage.isCreateValidationVisible())
                .as("Порожній submit має показати «Додайте хоча б один ресурс»")
                .isTrue();
    }
}
