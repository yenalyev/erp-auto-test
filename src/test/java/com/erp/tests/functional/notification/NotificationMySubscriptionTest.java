package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
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
@Feature("REQ-NOTIF My notifications")
public class NotificationMySubscriptionTest extends BaseFunctionalTest {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final UserRole PRODUCTION_ORDER_ADMIN = UserRole.ORDER_PRODUCTION_WORKER;
    private static final UserRole REGULAR_OWNER = UserRole.DYNAMIC_LOCATION_OWNER;

    private NotificationFixture notifications;
    private UserFixture users;
    private Long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupActors() {
        notifications = new NotificationFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        StorageResponse storage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        storageId = storage.getId();

        UserFixture.BusinessActor orderAdmin = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(storage));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());

        UserFixture.BusinessActor productionAdmin = users.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER,
                List.of(storage),
                List.of("production-order.manage"));
        apiExecutor.setSessionForRole(
                PRODUCTION_ORDER_ADMIN, productionAdmin.username(), productionAdmin.password());

        UserFixture.BusinessActor regularOwner = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
        apiExecutor.setSessionForRole(
                REGULAR_OWNER, regularOwner.username(), regularOwner.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupActors() {
        unsubscribeQuietly(ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED);
        unsubscribeQuietly(PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED);
        apiExecutor.evictSessionForRole(ORDER_ADMIN);
        apiExecutor.evictSessionForRole(PRODUCTION_ORDER_ADMIN);
        apiExecutor.evictSessionForRole(REGULAR_OWNER);
        if (users != null) {
            users.deactivateTrackedUsers();
        }
    }

    @Test(priority = 1)
    @TestCaseId(value = "TC-NOTIF-040", roles = BusinessRole.ORDER_ADMIN)
    @Story("Order-created personal subscription")
    @Severity(SeverityLevel.CRITICAL)
    @Description("order.manage відкриває шаблон «Нове замовлення» та дозволяє зберегти персональну підписку.")
    public void orderAdminCanSubscribeToNewOrders() {
        assertThat(notifications.availableMyTemplateCodes(ORDER_ADMIN))
                .contains(NotificationDataFactory.TEMPLATE_ORDER_CREATED);

        notifications.subscribeMy(
                ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED, List.of(storageId));
        assertThat(notifications.isMySubscribed(
                ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED)).isTrue();

        notifications.unsubscribeMy(ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED);
        assertThat(notifications.isMySubscribed(
                ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED)).isFalse();
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-041")
    @Story("Production-order-created personal subscription")
    @Severity(SeverityLevel.CRITICAL)
    @Description("production-order.manage відкриває шаблон «Нове виробниче замовлення» та дозволяє зберегти персональну підписку.")
    public void productionOrderAdminCanSubscribeToNewProductionOrders() {
        assertThat(notifications.availableMyTemplateCodes(PRODUCTION_ORDER_ADMIN))
                .contains(NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED);

        notifications.subscribeMy(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED,
                List.of(storageId));
        assertThat(notifications.isMySubscribed(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED)).isTrue();

        notifications.unsubscribeMy(
                PRODUCTION_ORDER_ADMIN, NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED);
        assertThat(notifications.isMySubscribed(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED)).isFalse();
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-042")
    @Story("Removed personal templates")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видалені invoice_generated і tech_map_mode_changed не повертаються у «Моїх сповіщеннях».")
    public void removedTemplatesAreAbsentFromMyNotifications() {
        assertThat(notifications.availableMyTemplateCodes(REGULAR_OWNER))
                .doesNotContain(
                        NotificationDataFactory.REMOVED_TEMPLATE_INVOICE_GENERATED,
                        NotificationDataFactory.REMOVED_TEMPLATE_TECH_MAP_MODE_CHANGED);
    }

    private void unsubscribeQuietly(UserRole role, String templateCode) {
        if (notifications == null) {
            return;
        }
        try {
            notifications.unsubscribeMy(role, templateCode);
        } catch (Exception ignored) {
            // Best-effort cleanup; dynamic users are deactivated below.
        }
    }
}
