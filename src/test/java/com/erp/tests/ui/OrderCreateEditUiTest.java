package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.OrderListPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("REQ-ORD Orders UI")
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
            У формі видимий селектор локації доставки.
            Submit без рядків → валідація «Додайте хоча б один ресурс».
            """)
    public void createDialogShowsValidationOnEmptySubmit() {
        OrderListPage ordersPage = new OrderListPage(page).open();

        if (!ordersPage.isCreateButtonVisible()) {
            throw new AssertionError("Create order button not visible for 3bat on requester UNIT");
        }
        if (ordersPage.isCreateDisabled()) {
            throw new AssertionError("Create order button disabled — cannot open create dialog");
        }

        ordersPage.clickCreateOrder();

        assertThat(ordersPage.isDeliveryStorageSelectorVisible())
                .as("Форма створення має містити селектор локації доставки")
                .isTrue();

        ordersPage.submitCreateDialog();

        assertThat(ordersPage.isCreateValidationVisible())
                .as("Порожній submit має показати «Додайте хоча б один ресурс»")
                .isTrue();
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-UI-008")
    @Story("Delivery location selector types")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Селектор доставки показує активні UNIT, STORAGE і PRODUCTION.
            CREW та FLY_POINT відсутні у списку опцій.
            """)
    public void deliverySelectorContainsOnlySupportedLocationTypes() {
        try {
            StorageResponse unit = storageFixture.createUnitStorage(requesterStorageId, "ord-delivery-unit-");
            StorageResponse storage = storageFixture.createChildStorage(
                    requesterStorageId, "ord-delivery-storage-", UnitType.STORAGE, StorageRelation.INTERNAL);
            StorageResponse production = storageFixture.createChildStorage(
                    requesterStorageId, "ord-delivery-production-", UnitType.PRODUCTION, StorageRelation.INTERNAL);
            StorageResponse crew = storageFixture.createCrewStorage(requesterStorageId, "ord-delivery-crew-");
            StorageResponse flyPoint = storageFixture.createFlyPointStorage(requesterStorageId, "ord-delivery-fly-");

            loginAsAdmin();
            OrderListPage ordersPage = new OrderListPage(page).open().clickCreateOrder();

            List<String> initialOptions = ordersPage
                    .openDeliveryStorageSelector()
                    .collectDeliveryStorageOptionLabels();
            assertThat(initialOptions)
                    .as("Початковий список не містить CREW")
                    .noneMatch(label -> label.contains(crew.getName()));
            assertThat(initialOptions)
                    .as("Початковий список не містить FLY_POINT")
                    .noneMatch(label -> label.contains(flyPoint.getName()));

            for (StorageResponse allowed : List.of(unit, storage, production)) {
                assertThat(initialOptions)
                        .as("%s має бути доступна в selector", allowed.getType())
                        .anyMatch(label -> label.contains(allowed.getName()));
            }

            for (StorageResponse forbidden : List.of(crew, flyPoint)) {
                assertThat(initialOptions)
                        .as("%s не повинна з'являтися у selector", forbidden.getType())
                        .noneMatch(label -> label.contains(forbidden.getName()));
            }
        } finally {
            storageFixture.deactivateTrackedStorages(MANAGER);
        }
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-UI-009")
    @Story("Select explicit delivery location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Обрати STORAGE, відмінний від поточного workspace.
            Селектор зберігає явно обрану локацію доставки у формі.
            """)
    public void keepsExplicitlySelectedDeliveryStorage() {
        StorageResponse destination = storageFixture.getById(MANAGER, gatheringStorageId);
        assertThat(destination.getType())
                .as("Локація комплектації для сценарію має бути складом")
                .isEqualTo(UnitType.STORAGE.name());
        loginAsAdmin();
        OrderListPage ordersPage = new OrderListPage(page).open().clickCreateOrder();
        ordersPage.selectDeliveryStorageByName(destination.getName());

        assertThat(ordersPage.getSelectedDeliveryStorageLabel())
                .as("У формі має залишитися явно обрана локація доставки")
                .contains(destination.getName());
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-UI-006")
    @Story("Edit NEW order")
    @Description("Редагувати NEW (update): Зберегти видима; після take-to-work edit зникає.")
    public void saveVisibleOnNewThenHiddenAfterTakeToWork() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        OrderListPage ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        if (!ordersPage.isSaveButtonVisible()) {
            throw new AssertionError("«Зберегти» not visible for NEW order — check order::update for requester");
        }
        assertThat(ordersPage.isSaveButtonVisible()).isTrue();

        loginAsAdmin();
        orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);
        loginAsOwner();
        ordersPage = new OrderListPage(page).openDeepLink(order.getId());
        assertThat(ordersPage.isSaveButtonVisible())
                .as("Після take-to-work редагування має зникнути")
                .isFalse();
    }
}
