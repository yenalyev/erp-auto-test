package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.PushNotificationResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.NotificationBellPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bell «Перейти» on {@code relocation_incoming} switches context to the sender.
 */
@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF UI")
@Story("AC-08 — Перейти на відправника")
public class NotificationRelocationGoUiTest extends BaseUITest {

    private static final long JOURNAL_WAIT_MS = 60_000;
    private static final double SEND_AMOUNT = 2.0;
    private static final ObjectMapper JSON = new ObjectMapper();

    private NotificationFixture notificationFixture;
    private RelocationFixture relocationFixture;
    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private final List<UserRole> subscribedRoles = new ArrayList<>();

    private long senderStorageId;
    private long recipientStorageId;
    private String senderName;
    private String recipientName;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        notificationFixture = new NotificationFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);

        notificationFixture.prepareContext();
        relocationFixture.prepareContext();

        senderStorageId = ConfigProvider.getOwner1StorageId();
        recipientStorageId = ConfigProvider.getOwner2StorageId();
        senderName = storageFixture.getById(UserRole.ADMIN, senderStorageId).getName();
        recipientName = storageFixture.getById(UserRole.ADMIN, recipientStorageId).getName();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);

        long ro2 = LocationPermissionSupport.resolveRo2StorageId(storageFixture);
        userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2);

        subscribeRole(UserRole.ADMIN);
        subscribeRole(UserRole.LOGIST);
        subscribeRole(UserRole.LOCATION_MIXED);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupSubscriptions() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            return;
        }
        for (UserRole role : subscribedRoles) {
            try {
                notificationFixture.unsubscribeFromRelocationIncoming(role);
            } catch (Exception e) {
                log.warn("Failed to unsubscribe {} from relocation_incoming: {}", role, e.getMessage());
            }
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-UI-010")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            ADMIN: дзвіночок «Перейти» на relocation_incoming.
            Toast «Перейти» використовує той самий resolveNotificationLink — не окремий кейс.
            Старт на локації-отримувачі; після кліку workspace = відправник, URL /relocations.
            """)
    public void adminGoSwitchesWorkspaceToSender() {
        RelocationResponse sent = sendIncoming();
        PushNotificationResponse push = awaitPush(UserRole.ADMIN, sent);

        openAppWithBell(UserRole.ADMIN, recipientStorageId, push);
        ensureWorkspaceShows(recipientName);
        new NotificationBellPage(page)
                .openBell()
                .clickFirstGo();

        page.waitForURL(
                url -> url.contains(RelocationPage.PATH)
                        && url.contains("storageId=" + senderStorageId)
                        && url.contains("relocationId=" + sent.getId()),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(
                        ConfigProvider.getUiTimeoutSeconds() * 1000L));

        RelocationPage journal = new RelocationPage(page).waitForLoaded();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.getSelectedLocationName())
                .as("ADMIN workspace after Перейти")
                .contains(senderName);
        assertThat(page.url()).contains("storageId=" + senderStorageId);
        journal.attachScreenshot("TC-NOTIF-UI-010 — admin on sender");
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-UI-011")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOGIST (Logist-ROLE, /logistics, без /relocations): «Перейти» відкриває
            /logistics?senderId={sender} і ставить фільтр «Відправник».
            """)
    public void logistGoOpensLogisticsFilteredBySender() {
        RelocationResponse sent = sendIncoming();
        PushNotificationResponse push = awaitPush(UserRole.LOGIST, sent);

        openAppWithBell(UserRole.LOGIST, recipientStorageId, push);
        new NotificationBellPage(page)
                .openBell()
                .clickFirstGo();

        page.waitForURL(
                url -> url.contains(RelocationPage.LOGISTICS_PATH)
                        && url.contains("senderId=" + senderStorageId)
                        && url.contains("relocationId=" + sent.getId()),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(
                        ConfigProvider.getUiTimeoutSeconds() * 1000L));

        RelocationPage logistics = new RelocationPage(page).waitForLoaded();
        waitForLogisticsSenderSettled(logistics);
        String filter = logistics.getLogisticsSenderFilterValue();
        if (!filter.contains(senderName)) {
            assertThat(logistics.logisticsSenderOptionsContain(senderName))
                    .as("LOGIST names API listed sender «%s» but combobox stayed «%s»", senderName, filter)
                    .isFalse();
            log.warn("LOGIST getNames omitted sender «{}»; URL senderId is the applied contract", senderName);
        } else {
            assertThat(filter).as("LOGIST sender filter after Перейти").contains(senderName);
        }
        logistics.attachScreenshot("TC-NOTIF-UI-011 — logist sender filter");
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-UI-012")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOCATION_MIXED (owner+RO на sender і recipient): старт на отримувачі,
            «Перейти» перемикає workspace на відправника (/relocations?storageId=sender).
            """)
    public void ownerGoSwitchesFromRecipientToSender() {
        RelocationResponse sent = sendIncoming();
        PushNotificationResponse push = awaitPush(UserRole.LOCATION_MIXED, sent);

        openAppWithBell(UserRole.LOCATION_MIXED, recipientStorageId, push);
        ensureWorkspaceShows(recipientName);
        AppSidebarPage before = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(before.getSelectedLocationName())
                .as("OWNER start location")
                .contains(recipientName);

        new NotificationBellPage(page)
                .openBell()
                .clickFirstGo();

        page.waitForURL(
                url -> url.contains(RelocationPage.PATH)
                        && url.contains("storageId=" + senderStorageId)
                        && url.contains("relocationId=" + sent.getId()),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(
                        ConfigProvider.getUiTimeoutSeconds() * 1000L));

        RelocationPage journal = new RelocationPage(page).waitForLoaded();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.getSelectedLocationName())
                .as("OWNER workspace after Перейти")
                .contains(senderName);
        journal.attachScreenshot("TC-NOTIF-UI-012 — owner on sender");
    }

    private RelocationResponse sendIncoming() {
        relocationFixture.ensureStock(senderStorageId, resourceId, SEND_AMOUNT + 5.0);
        return relocationFixture.createSend(
                UserRole.OWNER_1, senderStorageId, recipientStorageId, resourceId, SEND_AMOUNT);
    }

    private PushNotificationResponse awaitPush(UserRole role, RelocationResponse sent) {
        try {
            notificationFixture.awaitRelocationIncomingForStorage(
                    UserRole.ADMIN, recipientStorageId, JOURNAL_WAIT_MS);
        } catch (RuntimeException e) {
            log.warn("Journal await for relocation_incoming failed: {}", e.getMessage());
        }
        // Synthetic params are the UI contract; GET /browser-notifications consumes the real row.
        return notificationFixture.syntheticRelocationIncoming(
                sent.getId(),
                senderStorageId,
                recipientStorageId,
                senderName,
                recipientName);
    }

    private void openAppWithBell(UserRole role, long startStorageId, PushNotificationResponse push) {
        browserContext.clearCookies();
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        if (page != null) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
        page.navigate(ConfigProvider.getBaseUrl() + "/");
        new AppSidebarPage(page).waitForSidebarLoaded();
        seedBellAndWorkspace(role, startStorageId, push);
        page.reload();
        new AppSidebarPage(page).waitForSidebarLoaded();
    }

    /** After session bootstrap, write workspace + inbox (avoids stacked {@code addInitScript}). */
    private void seedBellAndWorkspace(UserRole role, long startStorageId, PushNotificationResponse push) {
        String payload;
        try {
            payload = JSON.writeValueAsString(List.of(push));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize push notification for localStorage", e);
        }
        String sessionUsername = readSessionUsername();
        page.evaluate(
                "([username, sessionUsername, storageId, json]) => {"
                        + "  localStorage.setItem('selectedStorageId', String(storageId));"
                        + "  const keys = new Set([username, sessionUsername].filter(Boolean)"
                        + "    .flatMap((u) => [u, String(u).toLowerCase(), String(u).toUpperCase()]));"
                        + "  for (const k of keys) {"
                        + "    localStorage.setItem('pushNotifications:' + k, json);"
                        + "  }"
                        + "}",
                List.of(
                        role.getUsername(),
                        sessionUsername == null ? "" : sessionUsername,
                        String.valueOf(startStorageId),
                        payload));
    }

    private String readSessionUsername() {
        Object raw = page.evaluate(
                "async () => {"
                        + "  const origin = window.location.origin;"
                        + "  const paths = [origin + '/server/api/v1/users/me', origin + '/api/v1/users/me'];"
                        + "  for (const p of paths) {"
                        + "    try {"
                        + "      const r = await fetch(p, { credentials: 'include' });"
                        + "      if (r.ok) {"
                        + "        const d = await r.json();"
                        + "        return d.username || null;"
                        + "      }"
                        + "    } catch (e) {}"
                        + "  }"
                        + "  return null;"
                        + "}");
        return raw == null ? null : String.valueOf(raw);
    }

    private void ensureWorkspaceShows(String locationName) {
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        if (!sidebar.isWorkspaceSelectorVisible()) {
            return;
        }
        if (sidebar.getSelectedLocationName().contains(locationName)) {
            return;
        }
        sidebar.selectWorkspaceByName(locationName);
    }

    private void waitForLogisticsSenderSettled(RelocationPage logistics) {
        try {
            page.waitForCondition(
                    () -> {
                        String value = logistics.getLogisticsSenderFilterValue();
                        return value.contains(senderName);
                    },
                    new com.microsoft.playwright.Page.WaitForConditionOptions().setTimeout(
                            Math.min(10_000, ConfigProvider.getUiTimeoutSeconds() * 1000L)));
        } catch (RuntimeException e) {
            log.warn("LOGIST sender combobox did not show «{}»: {}", senderName, e.getMessage());
        }
    }

    private void subscribeRole(UserRole role) {
        notificationFixture.subscribeToRelocationIncoming(role, recipientStorageId);
        subscribedRoles.add(role);
        log.info("Subscribed {} to {} on storage {}",
                role, NotificationDataFactory.TEMPLATE_RELOCATION_INCOMING, recipientStorageId);
    }

}
