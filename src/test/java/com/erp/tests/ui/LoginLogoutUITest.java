package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.LoginPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI auth tests for dev environment — login and logout under ADMIN and OWNER_1 roles.
 *
 * TC-UI-003 — login as admin
 * TC-UI-004 — login as owner1
 * TC-UI-005 — logout as admin
 * TC-UI-006 — logout as owner1
 */
@Slf4j
@Epic("Authentication & Authorization")
@Feature("Login / Logout UI")
public class LoginLogoutUITest extends BaseUITest {

    private static final String POST_LOGIN_PATH = "/production";

    /** Each test must start unauthenticated — context is shared at class level. */
    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        browserContext.clearCookies();
        super.testSetup();
    }

    @Test(priority = 1)
    @TestCaseId("TC-UI-003")
    @Story("Login UI — admin")
    @Severity(SeverityLevel.BLOCKER)
    @Description("ADMIN: відкрити форму логіну, увійти та дочекатися редиректу на /production з username у sidebar.")
    public void testLoginAsAdmin() {
        performLogin(UserRole.ADMIN);
    }

    @Test(priority = 2)
    @TestCaseId("TC-UI-004")
    @Story("Login UI — owner1")
    @Severity(SeverityLevel.BLOCKER)
    @Description("OWNER_1: відкрити форму логіну, увійти та дочекатися редиректу на /production з username у sidebar.")
    public void testLoginAsOwner1() {
        performLogin(UserRole.OWNER_1);
    }

    @Test(priority = 3)
    @TestCaseId("TC-UI-005")
    @Story("Logout UI — admin")
    @Severity(SeverityLevel.BLOCKER)
    @Description("ADMIN: увійти, натиснути «Вийти» у sidebar та перевірити повернення на форму логіну.")
    public void testLogoutAsAdmin() {
        performLogout(UserRole.ADMIN);
    }

    @Test(priority = 4)
    @TestCaseId("TC-UI-006")
    @Story("Logout UI — owner1")
    @Severity(SeverityLevel.BLOCKER)
    @Description("OWNER_1: увійти, натиснути «Вийти» у sidebar та перевірити повернення на форму логіну.")
    public void testLogoutAsOwner1() {
        performLogout(UserRole.OWNER_1);
    }

    private void performLogin(UserRole role) {
        String username = role.getUsername();
        String password = role.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("Login test — role={}, user={}", role, username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);

        assertThat(loginPage.isLoginFormVisible())
                .as("Keycloak login form should be visible")
                .isTrue();

        loginPage.attachScreenshot("Login form — " + role);

        String landingUrl = loginPage.login(username, password, POST_LOGIN_PATH);

        assertThat(landingUrl)
                .as("After login the browser should land on the production journal")
                .contains(POST_LOGIN_PATH);

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.waitForLoaded();

        assertThat(productionPage.isLoaded())
                .as("Production journal page should be rendered after login")
                .isTrue();

        productionPage.attachScreenshot("Production page after login — " + role);

        Allure.parameter("Role", role.name());
        Allure.parameter("User", username);
        Allure.parameter("Landing URL", landingUrl);
        log.info("Login PASSED — role={}, url={}", role, landingUrl);
    }

    private void performLogout(UserRole role) {
        String username = role.getUsername();
        String password = role.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("Logout test — role={}, user={}", role, username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);

        assertThat(loginPage.isLoginFormVisible())
                .as("Keycloak login form should be visible before logout test login")
                .isTrue();

        loginPage.login(username, password, POST_LOGIN_PATH);

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.waitForLoaded();

        assertThat(productionPage.isLoaded())
                .as("Production journal page should be rendered before logout")
                .isTrue();

        AppSidebarPage sidebar = new AppSidebarPage(page);
        assertThat(sidebar.isUserMenuVisible(username))
                .as("Sidebar should show username '%s' before logout", username)
                .isTrue();

        sidebar.openUserMenu(username).logout().waitForLoggedOut();

        sidebar.attachScreenshot("After logout — " + role);

        String currentUrl = loginPage.currentUrl();
        assertThat(currentUrl.contains("/realms/") || currentUrl.contains("/login"))
                .as("After logout the browser should be on Keycloak or login URL, got: %s", currentUrl)
                .isTrue();

        if (loginPage.isLoginFormVisible()) {
            loginPage.attachScreenshot("Login form after logout — " + role);
        }

        Allure.parameter("Role", role.name());
        Allure.parameter("User", username);
        Allure.parameter("URL after logout", currentUrl);
        log.info("Logout PASSED — role={}, url={}", role, currentUrl);
    }
}
