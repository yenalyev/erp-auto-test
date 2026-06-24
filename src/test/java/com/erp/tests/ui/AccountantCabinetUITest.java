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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke for the accountant role cabinet after browser login.
 *
 * TC-UI-ACC-001 — login via form, verify sidebar sections, filters and default landing state.
 * TC-UI-ACC-002 — login and logout via sidebar user menu.
 */
@Slf4j
@Epic("Authentication & Authorization")
@Feature("Accountant cabinet UI")
public class AccountantCabinetUITest extends BaseUITest {

    private static final String POST_LOGIN_PATH = "/production";

    private static final List<String> EXPECTED_MAIN_SECTIONS = List.of(
            "Виробництво",
            "Несерійне виробництво",
            "Залишки",
            "Обладнання",
            "Логістика",
            "Експорт даних"
    );

    private static final List<String> EXPECTED_DICTIONARIES = List.of(
            "Техкарти",
            "Словник ресурсів",
            "Ціни"
    );

    /** Fresh session — no cookies or persisted storage selection from prior tests. */
    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        browserContext.clearCookies();
        browserContext.addInitScript("localStorage.clear();");
        super.testSetup();
    }

    @Test
    @TestCaseId("TC-UI-ACC-001")
    @Story("Accountant cabinet — login and layout smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Бухгалтер (accountant) входить через форму Keycloak.
            Після логіну перевіряється:
            — редирект на /production і активний розділ «Виробництво»
            — селектор локацій у sidebar (перша доступна локація обрана за замовчуванням)
            — розділи: Виробництво, Несерійне виробництво, Залишки, Обладнання, Логістика, Експорт даних
            — словники: Техкарти, Словник ресурсів, Ціни
            — фільтри журналу: Продукт, Категорія, Тип робіт, З, По
            """)
    public void accountantCabinetLayoutSmokeTest() {
        String username = UserRole.ACCOUNTANT.getUsername();
        String password = UserRole.ACCOUNTANT.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("TC-UI-ACC-001: Accountant cabinet smoke — user={}", username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);

        assertThat(loginPage.isLoginFormVisible())
                .as("Форма логіну Keycloak має бути видимою")
                .isTrue();

        loginPage.attachScreenshot("Login form — accountant");

        String landingUrl = loginPage.login(username, password, POST_LOGIN_PATH);

        assertThat(landingUrl)
                .as("Після логіну браузер має перейти на /production")
                .contains(POST_LOGIN_PATH);

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.waitForLoaded();

        AppSidebarPage sidebar = new AppSidebarPage(page);

        productionPage.attachScreenshot("Accountant cabinet — after login");

        assertThat(sidebar.isSidebarVisible())
                .as("Sidebar має бути відрендерений після логіну")
                .isTrue();

        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("Селектор локацій («Робочий простір») має бути видимим")
                .isTrue();

        Allure.step("Перевірити дефолтний вибір першої локації в селекторі", () -> {
            String selectedLocation = sidebar.getSelectedLocationName();
            String firstLocation = sidebar.getFirstAvailableLocationName();

            assertThat(selectedLocation)
                    .as("Обрана локація не повинна бути порожньою")
                    .isNotBlank();
            assertThat(selectedLocation)
                    .as("За замовчуванням має бути обрана перша доступна локація")
                    .isEqualTo(firstLocation);

            Allure.parameter("selectedLocation", selectedLocation);
            Allure.parameter("firstAvailableLocation", firstLocation);
        });

        assertThat(sidebar.isNavItemActive("Виробництво"))
                .as("Розділ «Виробництво» має бути активним на /production")
                .isTrue();

        for (String section : EXPECTED_MAIN_SECTIONS) {
            assertThat(sidebar.isNavItemVisible(section))
                    .as("Розділ sidebar «%s» має бути видимим", section)
                    .isTrue();
        }

        assertThat(sidebar.isDictionariesSectionVisible())
                .as("Блок «Словники» має бути видимим")
                .isTrue();

        for (String dictionary : EXPECTED_DICTIONARIES) {
            assertThat(sidebar.isDictionaryItemVisible(dictionary))
                    .as("Пункт словника «%s» має бути видимим", dictionary)
                    .isTrue();
        }

        assertThat(productionPage.isProductFilterVisible())
                .as("Фільтр «Продукт» має бути видимим")
                .isTrue();
        assertThat(productionPage.isCategoryFilterVisible())
                .as("Фільтр «Категорія» має бути видимим")
                .isTrue();
        assertThat(productionPage.isWorkTypeFilterVisible())
                .as("Фільтр «Тип робіт» має бути видимим")
                .isTrue();
        assertThat(productionPage.isDateFromVisible())
                .as("Фільтр «З» має бути видимим")
                .isTrue();
        assertThat(productionPage.isDateToVisible())
                .as("Фільтр «По» має бути видимим")
                .isTrue();

        productionPage.attachScreenshot("Accountant cabinet — all assertions passed");

        Allure.parameter("User", username);
        Allure.parameter("Landing URL", landingUrl);
        log.info("TC-UI-ACC-001 PASSED — url: {}", landingUrl);
    }

    @Test(priority = 2)
    @TestCaseId("TC-UI-ACC-002")
    @Story("Accountant cabinet — logout")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Бухгалтер (accountant) входить через форму Keycloak, відкриває меню користувача
            у sidebar і натискає «Вийти». Перевіряється повернення на сторінку логіну Keycloak.
            """)
    public void accountantLogoutTest() {
        performLogout();
    }

    private void performLogout() {
        String username = UserRole.ACCOUNTANT.getUsername();
        String password = UserRole.ACCOUNTANT.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();

        log.info("TC-UI-ACC-002: Accountant logout — user={}", username);

        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);

        assertThat(loginPage.isLoginFormVisible())
                .as("Форма логіну Keycloak має бути видимою перед тестом logout")
                .isTrue();

        loginPage.attachScreenshot("Login form — accountant");

        loginPage.login(username, password, POST_LOGIN_PATH);

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.waitForLoaded();

        assertThat(productionPage.isLoaded())
                .as("Журнал виробництва має бути відрендерений перед logout")
                .isTrue();

        productionPage.attachScreenshot("Accountant cabinet — after login");

        AppSidebarPage sidebar = new AppSidebarPage(page);
        assertThat(sidebar.isUserMenuVisible())
                .as("Меню користувача у footer sidebar має бути видимим перед logout")
                .isTrue();

        sidebar.openUserMenu().logout().waitForLoggedOut();

        loginPage.attachScreenshot("After logout — accountant");

        String currentUrl = loginPage.currentUrl();
        assertThat(currentUrl.contains("/realms/") || currentUrl.contains("/login"))
                .as("Після logout браузер має бути на Keycloak або /login, отримано: %s", currentUrl)
                .isTrue();

        Allure.parameter("User", username);
        Allure.parameter("URL after logout", currentUrl);
        log.info("TC-UI-ACC-002 PASSED — url: {}", currentUrl);
    }
}
