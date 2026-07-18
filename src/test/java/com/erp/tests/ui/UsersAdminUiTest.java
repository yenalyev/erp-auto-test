package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.UserModelResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionPage;
import com.erp.pages.UsersAdminPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path UI flows for admin user management (/users).
 */
@Slf4j
@Epic("Administration")
@Feature("User management")
@Story("Happy path")
public class UsersAdminUiTest extends BaseUITest {

    private static final String UI_USER_PREFIX = "ui-usr-";
    private static final String SIDEBAR_ITEM = "Користувачі та ролі";

    private UserFixture userFixture;
    private UserModelResponse arrangedUser;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        userFixture = new UserFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupUsers() {
        userFixture.deactivateTrackedUsers();
        arrangedUser = null;
    }

    @Test
    @TestCaseId("TC-UI-USR-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN: landing /users — заголовок, таб Користувачі, колонки таблиці, кнопка «Новий користувач»")
    public void usersListLanding() {
        prepareAdminSession();
        UsersAdminPage usersPage = new UsersAdminPage(page).open();

        assertThat(usersPage.isListPageLoaded()).isTrue();
        assertThat(usersPage.isUsersTabVisible()).isTrue();
        assertThat(usersPage.areUserTableHeadersVisible()).isTrue();
        assertThat(usersPage.isNewUserButtonVisible()).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-001 — users list landing");
    }

    @Test
    @TestCaseId("TC-UI-USR-002")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: фільтр «Пошук за логіном» показує створеного користувача")
    public void searchFilterByUsername() {
        arrangedUser = arrangeUser(UI_USER_PREFIX + "search-");
        prepareAdminSession();

        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .searchByUsername(arrangedUser.getUsername());

        assertThat(usersPage.isUsernameVisibleInTable(arrangedUser.getUsername())).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-002 — search filter");
    }

    @Test
    @TestCaseId("TC-UI-USR-003")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: фільтр «Всі локації...» не ламає сторінку списку")
    public void storageFilterUpdatesTable() {
        prepareAdminSession();
        UsersAdminPage usersPage = new UsersAdminPage(page).open().selectFirstStorageFilter();

        assertThat(usersPage.isListPageLoaded()).isTrue();
        assertThat(usersPage.areUserTableHeadersVisible()).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-003 — storage filter");
    }

    @Test
    @TestCaseId("TC-UI-USR-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN: створення користувача — діалог з credentials, redirect на /users, login у таблиці")
    public void createUserHappyPath() {
        prepareAdminSession();
        String username = UI_USER_PREFIX + "create-" + System.currentTimeMillis();

        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .clickNewUser()
                .fillCreateForm(username, "UI", "Create")
                .submitCreate()
                .assertCredentialsDialogVisible();

        assertThat(usersPage.credentialsDialogShowsUsername(username)).isTrue();

        usersPage.dismissCredentialsDialog()
                .searchByUsername(username);

        assertThat(usersPage.isUsernameVisibleInTable(username)).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-004 — create user");

        userFixture.trackUserByUsername(username);
    }

    @Test
    @TestCaseId("TC-UI-USR-005")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: перегляд користувача — форма з заповненими полями")
    public void viewUserDetail() {
        arrangedUser = arrangeUser(UI_USER_PREFIX + "view-");
        prepareAdminSession();

        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .searchByUsername(arrangedUser.getUsername())
                .clickUsernameLink(arrangedUser.getUsername());

        assertThat(usersPage.getFirstNameFieldValue()).contains(arrangedUser.getFirstName());
        usersPage.attachScreenshot("TC-UI-USR-005 — view user");
    }

    @Test
    @TestCaseId("TC-UI-USR-006")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: редагування імені користувача — збереження та оновлення у таблиці")
    public void editUserFirstName() {
        arrangedUser = arrangeUser(UI_USER_PREFIX + "edit-");
        prepareAdminSession();
        String updatedFirstName = "Edited" + System.currentTimeMillis();

        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .searchByUsername(arrangedUser.getUsername())
                .clickUsernameLink(arrangedUser.getUsername());

        assertThat(usersPage.isFirstNameFieldEditable()).isTrue();

        usersPage.updateFirstName(updatedFirstName).saveUser()
                .searchByUsername(arrangedUser.getUsername())
                .clickUsernameLink(arrangedUser.getUsername());

        assertThat(usersPage.getFirstNameFieldValue()).contains(updatedFirstName);
        usersPage.attachScreenshot("TC-UI-USR-006 — edit user");
    }

    @Test
    @TestCaseId("TC-UI-USR-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: таб «Ролі» — діалог дозволів Administrator-ROLE")
    public void rolesTabShowsAdministratorPermissions() {
        prepareAdminSession();

        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .openRolesTab()
                .clickRoleName(UsersAdminPage.ADMINISTRATOR_ROLE);

        assertThat(usersPage.getVisibleRolePermissions())
                .isNotEmpty()
                .anyMatch(p -> p.startsWith("perm_"));
        usersPage.attachScreenshot("TC-UI-USR-007 — role permissions");
    }

    @Test
    @TestCaseId("TC-UI-USR-008")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN: навігація через sidebar «Користувачі та ролі» → /users")
    public void sidebarNavigationToUsers() {
        prepareAdminSession();
        openProductionWithSidebar();

        page.locator("[data-sidebar='sidebar']")
                .getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                        new com.microsoft.playwright.Locator.GetByRoleOptions().setName(SIDEBAR_ITEM))
                .click();

        UsersAdminPage usersPage = new UsersAdminPage(page).waitForListLoaded();

        assertThat(usersPage.isOnUsersListPath()).isTrue();
        assertThat(usersPage.isListPageLoaded()).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-008 — sidebar navigation");
    }

    private UserModelResponse arrangeUser(String prefix) {
        return userFixture.createTestUser(prefix);
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

    private void openProductionWithSidebar() {
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        new ProductionPage(page).waitForLoaded();
        new AppSidebarPage(page).waitForSidebarLoaded();
    }
}
