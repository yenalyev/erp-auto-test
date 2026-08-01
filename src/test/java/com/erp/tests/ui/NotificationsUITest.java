package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.NotificationsPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF UI")
public class NotificationsUITest extends BaseUITest {

    private NotificationFixture fixture;
    private String createdCaption;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new NotificationFixture(testContext, apiExecutor);
        fixture.prepareContext();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupRecipients() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            return;
        }
        fixture.disableTrackedRecipients(UserRole.ADMIN);
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-UI-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN: /notifications — заголовок і 4 вкладки")
    public void notificationsLandingTabs() {
        prepareAdminSession();
        NotificationsPage notifications = new NotificationsPage(page).open();

        assertThat(notifications.isPageLoaded()).isTrue();
        assertThat(notifications.areTabsVisible()).isTrue();
        assertThat(page.url()).contains(NotificationsPage.LIST_PATH);
        notifications.attachScreenshot("TC-NOTIF-UI-001 — landing tabs");
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN: створити отримувача через діалог")
    public void createRecipientViaDialog() {
        createdCaption = NotificationDataFactory.uniqueCaption();
        String phone = NotificationDataFactory.randomPhone();

        prepareAdminSession();
        NotificationsPage notifications = new NotificationsPage(page).open()
                .openRecipientsTab()
                .openCreateRecipientDialog()
                .fillRecipientForm(createdCaption, phone)
                .saveRecipientDialog();

        assertThat(notifications.isCaptionVisibleInTable(createdCaption)).isTrue();

        // Track for API cleanup (find by caption)
        fixture.getAllRecipients(UserRole.ADMIN).stream()
                .filter(r -> createdCaption.equals(r.getCaption()))
                .findFirst()
                .ifPresent(r -> fixture.trackRecipient(r.getId()));

        notifications.attachScreenshot("TC-NOTIF-UI-002 — recipient created");
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-UI-003")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: вкладка Шаблони не порожня")
    public void templatesTabHasSeededRows() {
        prepareAdminSession();
        NotificationsPage notifications = new NotificationsPage(page).open()
                .openTemplatesTab();

        assertThat(notifications.hasTemplateRows()).isTrue();
        notifications.attachScreenshot("TC-NOTIF-UI-003 — templates");
    }

    @Test(priority = 4)
    @TestCaseId("TC-NOTIF-UI-004")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: журнал показує колонки Статус і Спроба")
    public void journalTabRendersColumns() {
        prepareAdminSession();
        NotificationsPage notifications = new NotificationsPage(page).open()
                .openLogTab();

        assertThat(notifications.isLogTableRendered()).isTrue();
        notifications.attachScreenshot("TC-NOTIF-UI-004 — journal");
    }

    @Test(priority = 5)
    @TestCaseId("TC-NOTIF-UI-001")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: sidebar «Сповіщення» відкриває /notifications")
    public void sidebarNavigatesToNotifications() {
        prepareAdminSession();
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        new ProductionPage(page).waitForLoaded();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup("Сповіщення");

        NotificationsPage notifications = new NotificationsPage(page).waitForLoaded();
        assertThat(page.url()).contains(NotificationsPage.LIST_PATH);
        assertThat(notifications.isPageLoaded()).isTrue();
        notifications.attachScreenshot("TC-NOTIF-UI-001 — sidebar");
    }

    private void prepareAdminSession() {
        injectAllLocationsView();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
    }
}
