package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PushNotificationResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Notifications")
@Feature("REQ-NOTIF Order events")
public class OrderCreatedNotificationApiTest extends OrderApiTestBase {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;

    private NotificationFixture notifications;
    private UserFixture notificationUserFixture;
    private String requesterStorageName;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupNotificationRecipient() {
        notifications = new NotificationFixture(testContext, apiExecutor);
        notificationUserFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse requesterStorage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, requesterStorageId);
        requesterStorageName = requesterStorage.getName();
        UserFixture.BusinessActor orderAdmin = notificationUserFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(requesterStorage));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());
        notifications.subscribeMy(
                ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_ORDER_CREATED,
                List.of(requesterStorageId));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupNotificationRecipient() {
        if (notifications != null) {
            try {
                notifications.unsubscribeMy(
                        ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED);
            } catch (Exception ignored) {
                // Dynamic user cleanup below is authoritative.
            }
        }
        apiExecutor.evictSessionForRole(ORDER_ADMIN);
        if (notificationUserFixture != null) {
            notificationUserFixture.deactivateTrackedUsers();
        }
    }

    @Test
    @TestCaseId(value = "TC-NOTIF-050", roles = BusinessRole.ORDER_ADMIN)
    @Story("New order notification")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Підписаний адміністратор замовлень отримує рівно одне browser notification після створення нового замовлення у своїй локації.")
    public void newOrderNotifiesSubscribedOrderAdmin() {
        OrderResponse order = orderFixture.createOrder(
                REQUESTER, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        PushNotificationResponse notification = notifications.awaitBrowserNotification(
                ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_ORDER_CREATED,
                "order_id",
                null,
                30_000);

        assertThat(notification.getParams())
                .containsEntry("template_code", NotificationDataFactory.TEMPLATE_ORDER_CREATED)
                .containsEntry("storage_name", requesterStorageName);
        assertThat(notification.getTitle()).isNotBlank();
        assertThat(notification.getDescription()).isNotBlank();
    }
}
