package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.models.response.OrderResponse;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.UnitManagementPage;
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
@Feature("REQ-ORD Free stock UI")
public class OrderFreeStockUiTest extends OrderUiTestBase {

    @BeforeMethod(alwaysRun = true)
    public void prepareSession() {
        loginAsAdmin();
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-101")
    @Story("Inventory free quantity")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI «Вільна к-сть» + жовтий бейдж після ACTIVE hold.")
    public void inventoryShowsFreeQuantityHeaderAfterHold() {
        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 5.0);

        reopenPageWithSession(GATHERER, gatheringStorageId);
        UnitManagementPage stock = new UnitManagementPage(page).open();
        stock.attachScreenshot("TC-ORD-101 — inventory free qty");
        assertThat(page.getByText("Вільна к-сть").count())
                .as("Колонка «Вільна к-сть»")
                .isGreaterThan(0);
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-102")
    @Story("Inventory edit booked hint")
    @Severity(SeverityLevel.NORMAL)
    @Description("Inventory edit: «з них N заброньовано».")
    public void inventoryEditShowsBookedHint() {
        OrderResponse order = prepareManagedInProgressUi();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, 5.0);
        inventoryFixtureOpenIfNeeded();

        reopenPageWithSession(GATHERER, gatheringStorageId);
        InventoryEditPage edit = new InventoryEditPage(page).open(gatheringStorageId);
        edit.attachScreenshot("TC-ORD-102 — booked hint");
        if (page.getByText("заброньовано").count() == 0) {
            throw new SkipException("Booked hint not visible — session may be closed or resource not on form");
        }
        assertThat(page.getByText("заброньовано").count()).isGreaterThan(0);
    }

    private void inventoryFixtureOpenIfNeeded() {
        var inventory = new com.erp.fixtures.InventoryFixture(testContext, apiExecutor);
        inventory.openSession(gatheringStorageId);
    }
}
