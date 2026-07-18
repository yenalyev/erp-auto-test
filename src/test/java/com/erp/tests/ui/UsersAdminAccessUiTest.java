package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.AccessForbiddenPage;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionPage;
import com.erp.pages.UsersAdminPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI access control for /users — admin-only sidebar and route guard.
 */
@Slf4j
@Epic("Administration")
@Feature("User management")
@Story("Access control")
public class UsersAdminAccessUiTest extends BaseUITest {

    private static final String SIDEBAR_ITEM = "Користувачі та ролі";
    private static final String LANDING_PATH = "/production";

    @Test
    @TestCaseId("TC-UI-USR-ACC-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN бачить пункт «Користувачі та ролі» у секції Словники")
    public void adminSeesUsersSidebarItem() {
        prepareSession(UserRole.ADMIN);
        openProductionWithSidebar();

        AppSidebarPage sidebar = new AppSidebarPage(page);

        assertThat(sidebar.isSidebarVisible()).isTrue();
        assertThat(sidebar.isDictionariesSectionVisible()).isTrue();
        assertThat(sidebar.isDictionaryItemVisible(SIDEBAR_ITEM))
                .as("ADMIN має бачити «Користувачі та ролі»")
                .isTrue();
        sidebar.attachScreenshot("TC-UI-USR-ACC-001 — admin sidebar");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 не бачить пункт «Користувачі та ролі» у sidebar")
    public void ownerDoesNotSeeUsersSidebarItem() {
        prepareSession(UserRole.OWNER_1);
        openProductionWithSidebar();

        AppSidebarPage sidebar = new AppSidebarPage(page);

        assertThat(sidebar.isSidebarVisible()).isTrue();
        assertThat(sidebar.isDictionaryItemVisible(SIDEBAR_ITEM))
                .as("OWNER_1 не має бачити «Користувачі та ролі»")
                .isFalse();
        sidebar.attachScreenshot("TC-UI-USR-ACC-002 — owner sidebar");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ACCOUNTANT не бачить пункт «Користувачі та ролі» у sidebar")
    public void accountantDoesNotSeeUsersSidebarItem() {
        prepareSession(UserRole.ACCOUNTANT);
        openProductionWithSidebar();

        AppSidebarPage sidebar = new AppSidebarPage(page);

        assertThat(sidebar.isSidebarVisible()).isTrue();
        assertThat(sidebar.isDictionaryItemVisible(SIDEBAR_ITEM))
                .as("ACCOUNTANT не має бачити «Користувачі та ролі»")
                .isFalse();
        sidebar.attachScreenshot("TC-UI-USR-ACC-003 — accountant sidebar");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 отримує 403 при прямому переході на /users")
    public void ownerDirectUsersRouteIsForbidden() {
        prepareSession(UserRole.OWNER_1);

        AccessForbiddenPage forbidden = new AccessForbiddenPage(page).open(UsersAdminPage.LIST_PATH);

        assertThat(forbidden.isForbiddenMessageVisible()).isTrue();
        forbidden.attachScreenshot("TC-UI-USR-ACC-004 — owner /users forbidden");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 отримує 403 при прямому переході на /users/create")
    public void ownerDirectCreateRouteIsForbidden() {
        prepareSession(UserRole.OWNER_1);

        AccessForbiddenPage forbidden = new AccessForbiddenPage(page).open(UsersAdminPage.CREATE_PATH);

        assertThat(forbidden.isForbiddenMessageVisible()).isTrue();
        forbidden.attachScreenshot("TC-UI-USR-ACC-005 — owner /users/create forbidden");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-006")
    @Severity(SeverityLevel.CRITICAL)
    @Description("CREW_MANAGER отримує 403 при прямому переході на /users")
    public void crewManagerDirectUsersRouteIsForbidden() {
        prepareSession(UserRole.CREW_MANAGER);

        AccessForbiddenPage forbidden = new AccessForbiddenPage(page).open(UsersAdminPage.LIST_PATH);

        assertThat(forbidden.isForbiddenMessageVisible()).isTrue();
        forbidden.attachScreenshot("TC-UI-USR-ACC-006 — crew manager /users forbidden");
    }

    @Test
    @TestCaseId("TC-UI-USR-ACC-007")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN відкриває /users і бачить заголовок сторінки")
    public void adminCanOpenUsersPage() {
        prepareSession(UserRole.ADMIN);

        UsersAdminPage usersPage = new UsersAdminPage(page).open();

        assertThat(usersPage.isListPageLoaded()).isTrue();
        usersPage.attachScreenshot("TC-UI-USR-ACC-007 — admin users page");
    }

    private void prepareSession(UserRole role) {
        injectAllLocationsView();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
    }

    private void openProductionWithSidebar() {
        page.navigate(ConfigProvider.getBaseUrl() + LANDING_PATH);
        new ProductionPage(page).waitForLoaded();
        new AppSidebarPage(page).waitForSidebarLoaded();
    }
}
