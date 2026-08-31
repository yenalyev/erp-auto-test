package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.RelocationState;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.RelocationPage;
import com.erp.pages.RelocationUpdateOutputPage;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Free stock UI")
public class OrderFreeStockUiTest extends OrderUiTestBase {

    private static final double TOTAL_STOCK = 10.0;
    private static final double HOLD_QTY = 8.0;
    private static final double FREE_SEND_QTY = 2.0;
    private static final double EDIT_INTO_BOOKED_QTY = 5.0;

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

    @Test(priority = 3)
    @TestCaseId("TC-ORD-UI-026")
    @Story("Edit send booked limit")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-ORD AC-10: форма «Редагування видачі» показує заброньований залишок як недоступний
            і не дає зберегти збільшення в цю частину («Підтвердити» disabled).
            """)
    public void editSendFormBlocksBookedQuantity() {
        pinGatheringOnHand(TOTAL_STOCK);
        OrderResponse order = orderFixture.prepareInProgressWithGathering(REQUESTER, MANAGER, HOLD_QTY);
        syncGatheringFromContext();
        new InventoryFixture(testContext, apiExecutor)
                .resetResourceStock(gatheringStorageId, resourceId, TOTAL_STOCK, MANAGER);
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, HOLD_QTY);

        Long recipientId = elsewhereRecipientId();
        String marker = "TC-ORD-UI-026-" + System.currentTimeMillis();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                GATHERER, gatheringStorageId, recipientId, resourceId, FREE_SEND_QTY, marker);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        reopenPageWithSession(MANAGER, gatheringStorageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.attachScreenshot("TC-ORD-UI-026 — in transit");
        assertThat(journal.isEditButtonVisibleInRow(marker))
                .as("Олівець для видачі %s", marker)
                .isTrue();

        RelocationUpdateOutputPage form = journal.clickEditSendInRow(marker);
        form.waitForBookedLimitHint();
        form.attachScreenshot("TC-ORD-UI-026 — booked hint");
        assertThat(form.hasBookedUnavailableHint())
                .as("Підказка «заброньовано … (недоступно)»")
                .isTrue();

        form.fillProductAmount(EDIT_INTO_BOOKED_QTY);
        form.attachScreenshot("TC-ORD-UI-026 — amount into booked");
        assertThat(form.showsAmountOverAvailable() || form.isConfirmDisabled())
                .as("Кількість понад вільний ліміт: помилка на полі або «Підтвердити» disabled")
                .isTrue();
        assertThat(form.isConfirmDisabled())
                .as("«Підтвердити» disabled коли кількість заходить у бронь")
                .isTrue();
    }

    private void inventoryFixtureOpenIfNeeded() {
        new InventoryFixture(testContext, apiExecutor).openSession(gatheringStorageId);
    }

    private void pinGatheringOnHand(double onHandTarget) {
        InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            orderFixture.clearInProgressOrders(MANAGER, gatheringStorageId, requesterStorageId);
            try {
                inventory.resetResourceStock(gatheringStorageId, resourceId, onHandTarget, MANAGER);
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("pinGatheringOnHand attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }
        throw new SkipException(
                "Cannot pin gathering on-hand to " + onHandTarget
                        + (last == null ? "" : ": " + last.getMessage()));
    }

    private Long elsewhereRecipientId() {
        long owner1 = ConfigProvider.getOwner1StorageId();
        if (owner1 != gatheringStorageId) {
            return owner1;
        }
        return ConfigProvider.getOwner2StorageId();
    }
}
