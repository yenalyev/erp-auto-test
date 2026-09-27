package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.NotificationUserSubscriptionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.MyNotificationsPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Notifications")
@Feature("Order relocation subscriptions UI")
public class OrderRelocationMyNotificationsUITest extends BaseUITest {

    private static final UserRole SINGLE_OWNER = UserRole.DYNAMIC_LOCATION_OWNER;
    private static final UserRole MULTI_OWNER = UserRole.LOCATION_MIXED;
    private static final String DESCRIPTION = "Замовлення переміщення";

    private NotificationFixture notifications;
    private StorageFixture storages;
    private UserFixture users;
    private UserFixture.BusinessActor singleOwner;
    private UserFixture.BusinessActor multiOwner;
    private StorageResponse singleSource;
    private StorageResponse parent;
    private StorageResponse child;
    private StorageResponse otherAllowed;
    private StorageResponse foreign;
    private String templateCode;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupActors() {
        notifications = new NotificationFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        long root = ConfigProvider.getOrderAvailabilityRootStorageId();
        singleSource = storages.createChildStorage(root, "notif-ui-single-");
        parent = storages.createChildStorage(root, "notif-ui-parent-");
        child = storages.createChildStorage(parent.getId(), "notif-ui-child-");
        otherAllowed = storages.createChildStorage(root, "notif-ui-other-");
        foreign = storages.createChildStorage(root, "notif-ui-foreign-");
        singleOwner = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER, List.of(singleSource));
        multiOwner = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER, List.of(parent, otherAllowed));
        apiExecutor.setSessionForRole(SINGLE_OWNER, singleOwner.username(), singleOwner.password());
        apiExecutor.setSessionForRole(MULTI_OWNER, multiOwner.username(), multiOwner.password());

        templateCode = notifications.getMyConfiguration(MULTI_OWNER).getTemplates().values().stream()
                .flatMap(List::stream)
                .filter(t -> DESCRIPTION.equals(t.getDescription()))
                .map(t -> t.getCode())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Шаблон «Замовлення переміщення» відсутній"));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupActors() {
        if (notifications != null && templateCode != null) {
            notifications.unsubscribeMy(SINGLE_OWNER, templateCode);
            notifications.unsubscribeMy(MULTI_OWNER, templateCode);
        }
        apiExecutor.evictSessionForRole(SINGLE_OWNER);
        apiExecutor.evictSessionForRole(MULTI_OWNER);
        if (users != null) users.deactivateTrackedUsers();
        if (storages != null) {
            for (StorageResponse location : Arrays.asList(child, parent, singleSource,
                    otherAllowed, foreign)) {
                if (location != null) storages.archiveStorage(UserRole.ADMIN, location.getId());
            }
        }
    }

    @TestCaseId(value = "TC-NOTIF-UI-022", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("Керівник локації вибирає свою локацію в підписці на замовлення переміщення.")
    public void singleOwnerSelectsOwnLocation() {
        loginAs(singleOwner);
        MyNotificationsPage settings = openSettings();
        assertThat(settings.isNotificationVisible(DESCRIPTION)).isTrue();
        settings.setNotificationEnabled(DESCRIPTION, true);
        settings.selectLocation(DESCRIPTION, singleSource.getName());
        awaitSubscription(SINGLE_OWNER, List.of(singleSource.getId()));
        page.reload();
        settings.waitForLoaded();
        assertThat(settings.isLocationSelected(DESCRIPTION, singleSource.getName())).isTrue();
    }

    @TestCaseId(value = "TC-NOTIF-UI-023", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("Керівник мультилокацій вибирає батьківську локацію і «Всі локації».")
    public void multiOwnerSelectsParentAndAll() {
        loginAs(multiOwner);
        MyNotificationsPage settings = openSettings();
        assertThat(settings.isNotificationVisible(DESCRIPTION)).isTrue();
        settings.setNotificationEnabled(DESCRIPTION, true);
        settings.selectLocation(DESCRIPTION, parent.getName());
        awaitSubscription(MULTI_OWNER, List.of(parent.getId()));
        page.reload();
        settings.waitForLoaded();
        assertThat(settings.isLocationSelected(DESCRIPTION, parent.getName())).isTrue();

        settings.selectLocation(DESCRIPTION, "Всі");
        PollUtils.waitUntilTrue(() -> {
            List<Long> ids = subscribedLocationIds(MULTI_OWNER);
            return ids.containsAll(List.of(parent.getId(), otherAllowed.getId()))
                    && !ids.contains(foreign.getId());
        }, 15_000, "All accessible locations subscription");
        page.reload();
        settings.waitForLoaded();
        assertThat(settings.isLocationSelected(DESCRIPTION, "Всі")).isTrue();
    }

    private void awaitSubscription(UserRole role, List<Long> expectedIds) {
        PollUtils.waitUntilTrue(() -> subscribedLocationIds(role).equals(expectedIds),
                15_000, "Notification subscription locations for " + role);
    }

    private List<Long> subscribedLocationIds(UserRole role) {
        return notifications.getMyConfiguration(role).getSubscriptions().stream()
                .filter(s -> templateCode.equals(s.getTemplateCode()))
                .findFirst()
                .map(NotificationUserSubscriptionResponse::getStorages)
                .stream().flatMap(List::stream)
                .map(storage -> storage.getId())
                .toList();
    }

    private void loginAs(UserFixture.BusinessActor actor) {
        browserContext.clearCookies();
        Map<String, String> cookies = authService.getSessionForUser(actor.username(), actor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '"
                + actor.storageIds().getFirst() + "');");
    }

    private MyNotificationsPage openSettings() {
        page.navigate(ConfigProvider.getBaseUrl() + "/my-notifications");
        return new MyNotificationsPage(page).waitForLoaded();
    }
}
