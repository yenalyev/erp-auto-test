package com.erp.tests.functional.production_order;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.ProductionOrderFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.ProductionOrderResponse;
import com.erp.models.response.PushNotificationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Notifications")
@Feature("REQ-NOTIF Production order events")
public class ProductionOrderCreatedNotificationApiTest extends BaseFunctionalTest {

    private static final UserRole PRODUCTION_ORDER_ADMIN = UserRole.ADMIN;

    private ProductionOrderFixture productionOrders;
    private NotificationFixture notifications;
    private Long createdProductionOrderId;
    private long targetStorageId;
    private long resourceId;
    private String targetStorageName;
    private List<Long> previousAdminSubscriptionStorages;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupNotificationRecipient() {
        productionOrders = new ProductionOrderFixture(testContext, apiExecutor);
        notifications = new NotificationFixture(testContext, apiExecutor);
        new ResourceFixture(testContext, apiExecutor).prepareContext();
        targetStorageId = productionOrders.resolveTargetStorageId(UserRole.ADMIN);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();

        StorageResponse targetStorage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, targetStorageId);
        targetStorageName = targetStorage.getName();
        previousAdminSubscriptionStorages = notifications.getMyConfiguration(PRODUCTION_ORDER_ADMIN)
                .getSubscriptions().stream()
                .filter(s -> NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED.equals(s.getTemplateCode()))
                .findFirst()
                .map(s -> s.getStorages().stream().map(storage -> storage.getId()).toList())
                .orElse(null);
        notifications.subscribeMy(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED,
                List.of(targetStorageId));
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (createdProductionOrderId != null) {
            try {
                productionOrders.cancel(UserRole.ADMIN, createdProductionOrderId);
            } catch (Exception ignored) {
                productionOrders.deleteRaw(UserRole.ADMIN, createdProductionOrderId);
            }
        }
        if (notifications != null) {
            try {
                if (previousAdminSubscriptionStorages == null) {
                    notifications.unsubscribeMy(PRODUCTION_ORDER_ADMIN,
                            NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED);
                } else {
                    notifications.subscribeMy(PRODUCTION_ORDER_ADMIN,
                            NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED,
                            previousAdminSubscriptionStorages);
                }
            } catch (Exception ignored) {
                // Best-effort cleanup for the existing Admin account.
            }
        }
    }

    @Test
    @TestCaseId("TC-NOTIF-051")
    @Story("New production order notification")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Підписаний Admin отримує browser notification після створення нового виробничого замовлення у своїй локації.")
    public void newProductionOrderNotifiesSubscribedProductionAdmin() {
        ProductionOrderResponse order = productionOrders.create(
                UserRole.ADMIN,
                productionOrders.buildCreateRequest(targetStorageId, resourceId, 2.0));
        createdProductionOrderId = order.getId();

        PushNotificationResponse notification = notifications.awaitBrowserNotification(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED,
                "production_order_id",
                order.getId(),
                30_000);

        assertThat(notification.getParams())
                .containsEntry("template_code",
                        NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED)
                .containsEntry("production_order_id", String.valueOf(order.getId()))
                .containsEntry("storage_name", targetStorageName);
        assertThat(notification.getTitle()).isNotBlank();
        assertThat(notification.getDescription()).isNotBlank();
    }
}
