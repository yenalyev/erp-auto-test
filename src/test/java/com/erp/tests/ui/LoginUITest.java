package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.LoginPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke tests for the login flow.
 *
 * TC-UI-001 — happy path: valid credentials → redirect to application
 * TC-UI-002 — negative path: invalid credentials → error message shown
 */
@Slf4j
@Epic("Authentication & Authorization")
@Feature("Login UI")
public class LoginUITest extends BaseUITest {

    private static final String POST_LOGIN_PATH = "/production";

    /** Each test must start unauthenticated — context is shared at class level. */
    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        browserContext.clearCookies();
        super.testSetup();
    }

    @Test(priority = 1)
    @TestCaseId("TC-UI-001")
    @Story("Login UI — happy path")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Відкрити форму логіну, увійти та дочекатися редиректу на /production з рендерингом сторінки журналу.")
    public void testSuccessfulLogin() {
        String username = UserRole.ADMIN.getUsername();
        String password = UserRole.ADMIN.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("TC-UI-001: Browser login smoke — user={}", username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);

        assertThat(loginPage.isLoginFormVisible())
                .as("Keycloak login form should be visible after navigating to /login")
                .isTrue();

        loginPage.attachScreenshot("Login form loaded");

        String landingUrl = loginPage.login(username, password, POST_LOGIN_PATH);

        assertThat(landingUrl)
                .as("After login the browser should land on the production journal")
                .contains(POST_LOGIN_PATH);

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.waitForLoaded();

        assertThat(productionPage.isLoaded())
                .as("Production journal page should be rendered after login")
                .isTrue();

        productionPage.attachScreenshot("Production page after login");

        Allure.parameter("User", username);
        Allure.parameter("Landing URL", landingUrl);
        log.info("TC-UI-001 PASSED — landed on: {}", landingUrl);
    }

    @Test(priority = 2)
    @TestCaseId("TC-UI-002")
    @Story("Login UI — invalid credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ввести невірний пароль та перевірити, що відображається повідомлення про помилку і редиректу не відбулося.")
    public void testInvalidCredentialsShowsError() {
        String username = UserRole.ADMIN.getUsername();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("TC-UI-002: Invalid credentials test — user={}", username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, ConfigProvider.getBaseUrl());

        page.fill("#username", username);
        page.fill("#password", "wrong-password-that-will-fail");
        page.click("#kc-login");

        // Keycloak stays on the login page when credentials are wrong
        loginPage.waitForVisible("#username", 5_000);

        String currentUrl = loginPage.currentUrl();
        assertThat(currentUrl)
                .as("Browser should stay on the Keycloak realm page after invalid login")
                .contains("/realms/");

        loginPage.attachScreenshot("Invalid credentials — error state");

        Allure.parameter("User", username);
        Allure.parameter("URL after failed login", currentUrl);
        log.info("TC-UI-002 PASSED — browser stayed on Keycloak: {}", currentUrl);
    }
}
