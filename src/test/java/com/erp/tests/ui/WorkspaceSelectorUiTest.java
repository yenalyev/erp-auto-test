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
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke for the sidebar workspace StorageTreeSelect («Робочий простір»).
 *
 * <p>TC-UI-WKS-001 — ADMIN switches from owner1 to owner2 via tree search + button click.
 */
@Slf4j
@Epic("Navigation")
@Feature("Workspace selector UI")
public class WorkspaceSelectorUiTest extends BaseUITest {

    private static final String POST_LOGIN_PATH = "/production";

    private StorageFixture storageFixture;
    private long owner1StorageId;
    private long owner2StorageId;
    private String owner1StorageName;
    private String owner2StorageName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        owner1StorageName = storageFixture.getNames(UserRole.ADMIN, true, null, owner1StorageId)
                .getFirst()
                .getName();
        owner2StorageName = storageFixture.getNames(UserRole.ADMIN, true, null, owner2StorageId)
                .getFirst()
                .getName();
    }

    @Test(priority = 1)
    @TestCaseId("TC-UI-WKS-001")
    @Story("StorageTreeSelect — select location in tree")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            ADMIN з кількома локаціями:
            1) старт з owner1 у localStorage;
            2) відкрити /production — видно «Робочий простір»;
            3) через StorageTreeSelect (пошук + button у дереві) обрати owner2;
            4) trigger показує ім'я owner2 і localStorage.selectedStorageId = owner2 id.
            """)
    public void adminSelectsLocationInWorkspaceTree() {
        prepareAuthenticatedPage(UserRole.ADMIN, owner1StorageId);

        page.navigate(ConfigProvider.getBaseUrl() + POST_LOGIN_PATH);
        new ProductionPage(page).waitForLoaded();

        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();

        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("Селектор «Робочий простір» має бути видимим для ADMIN з кількома локаціями")
                .isTrue();

        String selectedBefore = sidebar.getSelectedLocationName();
        assertThat(selectedBefore)
                .as("До перемикання trigger має показувати локацію owner1")
                .contains(owner1StorageName);

        sidebar.selectWorkspaceByName(owner2StorageName);

        String selectedAfter = sidebar.getSelectedLocationName();
        assertThat(selectedAfter)
                .as("Після кліку в дереві trigger має показувати локацію owner2")
                .contains(owner2StorageName);

        String storedId = (String) page.evaluate("() => localStorage.getItem('selectedStorageId')");
        assertThat(storedId)
                .as("localStorage.selectedStorageId має оновитися на id owner2")
                .isEqualTo(String.valueOf(owner2StorageId));

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
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        if (page != null) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
