package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.MyNotificationsPage;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Notifications")
@Feature("REQ-NOTIF My notifications UI")
public class MyNotificationsUITest extends BaseUITest {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final UserRole PRODUCTION_ORDER_ADMIN = UserRole.ADMIN;

    private NotificationFixture notifications;
    private UserFixture users;
    private UserFixture.BusinessActor orderAdmin;
    private Long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupActors() {
        notifications = new NotificationFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        StorageResponse storage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        storageId = storage.getId();

        orderAdmin = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(storage));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupActors() {
        unsubscribeQuietly(ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED);
        unsubscribeQuietly(PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED);
        apiExecutor.evictSessionForRole(ORDER_ADMIN);
        apiExecutor.evictSessionForRole(PRODUCTION_ORDER_ADMIN);
        if (users != null) {
            users.deactivateTrackedUsers();
        }
    }

    @Test(priority = 1)
    @TestCaseId(value = "TC-NOTIF-UI-020", roles = BusinessRole.ORDER_ADMIN)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Адміністратор замовлень вмикає «Нове замовлення» у вкладці «Мої сповіщення».")
    public void orderAdminSubscribesToNewOrdersInUi() {
        loginAs(orderAdmin);
        MyNotificationsPage pageObject = openMyNotifications();

        assertThat(pageObject.isNotificationVisible("Нове замовлення")).isTrue();
        pageObject.setNotificationEnabled("Нове замовлення", true);

        assertThat(notifications.isMySubscribed(
                ORDER_ADMIN, NotificationDataFactory.TEMPLATE_ORDER_CREATED)).isTrue();
        pageObject.attachScreenshot("TC-NOTIF-UI-020 — Нове замовлення enabled");
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-UI-021")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Глобальний Admin вмикає «Нове виробниче замовлення» у вкладці «Мої сповіщення». " +
            "Примітка: окремої ролі адміністратора виробничих замовлень наразі немає; " +
            "після її появи сценарій має бути переведений на permission-based роль з production-order.manage.")
    public void productionAdminSubscribesToNewProductionOrdersInUi() {
        loginAs(PRODUCTION_ORDER_ADMIN);
        MyNotificationsPage pageObject = openMyNotifications();

        assertThat(pageObject.isNotificationVisible("Нове виробниче замовлення")).isTrue();
        pageObject.setNotificationEnabled("Нове виробниче замовлення", true);

        assertThat(notifications.isMySubscribed(
                PRODUCTION_ORDER_ADMIN,
                NotificationDataFactory.TEMPLATE_PRODUCTION_ORDER_CREATED)).isTrue();
        pageObject.attachScreenshot("TC-NOTIF-UI-021 — Нове виробниче замовлення enabled");
    }

    private void loginAs(UserFixture.BusinessActor actor) {
        browserContext.clearCookies();
        Map<String, String> cookies = authService.getSessionForUser(actor.username(), actor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    private void loginAs(UserRole role) {
        browserContext.clearCookies();
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    private MyNotificationsPage openMyNotifications() {
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        new ProductionPage(page).waitForLoaded();
        new AppSidebarPage(page).waitForSidebarLoaded().openUserMenu();
        page.getByRole(
                        AriaRole.MENUITEM,
                        new com.microsoft.playwright.Page.GetByRoleOptions()
                                .setName(MyNotificationsPage.MENU_ITEM))
                .click();
        return new MyNotificationsPage(page).waitForLoaded();
    }

    private void unsubscribeQuietly(UserRole role, String templateCode) {
        if (notifications == null) {
            return;
        }
        try {
            notifications.unsubscribeMy(role, templateCode);
        } catch (Exception ignored) {
            // Dynamic user cleanup below is authoritative.
        }
    }
}
