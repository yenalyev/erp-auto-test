package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke for the sidebar workspace StorageTreeSelect («Робочий простір»).
 *
 * <p>TC-UI-WKS-001 — ADMIN switches between two isolated locations via tree search + button click.
 */
@Slf4j
@Epic("Navigation")
@Feature("Workspace selector UI")
public class WorkspaceSelectorUiTest extends BaseUITest {

    private static final String POST_LOGIN_PATH = "/production";

    private StorageFixture storageFixture;
    private long firstStorageId;
    private long secondStorageId;
    private String firstStorageName;
    private String secondStorageName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        long parentId = storageFixture.resolveParentUnit().getId();
        var first = storageFixture.createProductionStorage(parentId, "workspace-first-");
        var second = storageFixture.createProductionStorage(parentId, "workspace-second-");
        firstStorageId = first.getId();
        secondStorageId = second.getId();
        firstStorageName = first.getName();
        secondStorageName = second.getName();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupLocations() {
        if (storageFixture != null) {
            storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-UI-WKS-001")
    @Story("StorageTreeSelect — select location in tree")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            ADMIN з двома динамічно створеними локаціями:
            1) старт з першої у localStorage;
            2) відкрити /production — видно «Робочий простір»;
            3) через StorageTreeSelect (пошук + button у дереві) обрати другу;
            4) trigger показує ім'я другої і localStorage.selectedStorageId:<username> = її id.
            """)
    public void adminSelectsLocationInWorkspaceTree() {
        prepareAuthenticatedPage(UserRole.ADMIN, firstStorageId);

        page.navigate(ConfigProvider.getBaseUrl() + POST_LOGIN_PATH);
        new ProductionPage(page).waitForLoaded();

        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();

        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("Селектор «Робочий простір» має бути видимим для ADMIN з кількома локаціями")
                .isTrue();

        String selectedBefore = sidebar.getSelectedLocationName();
        assertThat(selectedBefore)
                .as("До перемикання trigger має показувати першу локацію")
                .contains(firstStorageName);

        sidebar.selectWorkspaceByName(secondStorageName);

        String selectedAfter = sidebar.getSelectedLocationName();
        assertThat(selectedAfter)
                .as("Після кліку в дереві trigger має показувати другу локацію")
                .contains(secondStorageName);

        String storedId = (String) page.evaluate(
                "() => localStorage.getItem('selectedStorageId:" + UserRole.ADMIN.getUsername() + "')");
        assertThat(storedId)
                .as("localStorage.selectedStorageId:<username> має оновитися на id другої локації")
                .isEqualTo(String.valueOf(secondStorageId));

        sidebar.attachScreenshot("TC-UI-WKS-001 — workspace switched");
    }

    /**
     * Fresh page + cookies + storage init script (init scripts apply only to new navigations).
     */
    private void prepareAuthenticatedPage(UserRole role, long selectedStorageId) {
        browserContext.clearCookies();
        var cookies = getPlaywrightSessionProvider().getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId:" + role.getUsername()
                        + "', '" + selectedStorageId + "');");
        if (page != null) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
