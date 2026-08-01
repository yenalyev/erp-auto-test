package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.LoginPage;
import com.erp.pages.ProductionPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final double SEND_AMOUNT = 5.0;

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private CrewRegionFixture crewFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private Long resourceId;
    private long owner1StorageId;
    private long owner2StorageId;

    private static final List<String> EXPECTED_MAIN_SECTIONS = List.of(
            "Виробництво",
            "Залишки",
            "Обладнання",
            "Логістика",
            "Експорт даних"
    );

    private static final List<String> EXPECTED_DICTIONARIES = List.of(
            "Техкарти",
            "Довідники ресурсів"
    );

    private static final List<String> EXPECTED_PRODUCTION_TABS = List.of(
            "Несерійне виробництво"
    );

    private static final List<String> EXPECTED_RESOURCE_TABS = List.of(
            "Словник ресурсів",
            "Ціни"
    );

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ResourceResponse resource = resourceFixture.createUniqueResource("acc-ui-rel-");
        resourceId = resource.getId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupAccountantCabinetArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    /** Fresh session — no cookies or persisted storage selection from prior tests. */
    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        browserContext.clearCookies();
        browserContext.addInitScript("localStorage.clear();");
        super.testSetup();
    }

    @Test
    @TestCaseId({
            "TC-UI-ACC-001",
            "TC-ACC-001"
    })
    @Story("Accountant cabinet — login and layout smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Бухгалтер (accountant) входить через форму Keycloak.
            Після логіну перевіряється:
            — редирект на /production і активний розділ «Виробництво»
            — селектор локацій у sidebar (перша доступна локація обрана за замовчуванням)
            — розділи sidebar: Виробництво, Залишки, Обладнання, Логістика, Експорт даних
            — словники: Техкарти, Довідники ресурсів (PageTabs: Словник ресурсів, Ціни)
            — PageTabs у «Виробництво»: Несерійне виробництво
            — фільтри журналу: Продукт, Категорія, Тип робіт, Період
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

        Allure.step("Перевірити дефолтний вибір локації в селекторі", () -> {
            String selectedLocation = sidebar.getSelectedLocationName();
            java.util.List<String> available = sidebar.collectWorkspaceLocationLabels();

            assertThat(selectedLocation)
                    .as("Обрана локація не повинна бути порожньою")
                    .isNotBlank();
            assertThat(available)
                    .as("Ієрархічний селектор має містити хоча б одну локацію")
                    .isNotEmpty();
            assertThat(available)
                    .as("Обрана локація має бути серед доступних у StorageTreeSelect")
                    .anyMatch(label -> label.equals(selectedLocation) || label.contains(selectedLocation)
                            || selectedLocation.contains(label));

            Allure.parameter("selectedLocation", selectedLocation);
            Allure.parameter("availableLocations", available.toString());
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

        for (String tab : EXPECTED_PRODUCTION_TABS) {
            assertThat(sidebar.isPageTabVisible(tab))
                    .as("PageTab «%s» має бути видимим на /production", tab)
                    .isTrue();
        }

        Allure.step("Перевірити PageTabs у групі «Довідники ресурсів»", () -> {
            sidebar.navigateToGroupedPage(
                    AppSidebarPage.GROUP_RESOURCES, AppSidebarPage.TAB_RESOURCES_DICT);
            for (String tab : EXPECTED_RESOURCE_TABS) {
                assertThat(sidebar.isPageTabVisible(tab))
                        .as("PageTab «%s» має бути видимим у довідниках ресурсів", tab)
                        .isTrue();
            }
            sidebar.openGroup(AppSidebarPage.GROUP_PRODUCTION);
            new ProductionPage(page).waitForLoaded();
        });

        assertThat(productionPage.isProductFilterVisible())
                .as("Фільтр «Продукт» має бути видимим")
                .isTrue();
        assertThat(productionPage.isCategoryFilterVisible())
                .as("Фільтр «Категорія» має бути видимим")
                .isTrue();
        assertThat(productionPage.isWorkTypeFilterVisible())
                .as("Фільтр «Тип робіт» має бути видимим")
                .isTrue();
        assertThat(productionPage.isPeriodFilterVisible())
                .as("Фільтр «Період» має бути видимим")
                .isTrue();

        productionPage.attachScreenshot("Accountant cabinet — all assertions passed");

        Allure.parameter("User", username);
        Allure.parameter("Landing URL", landingUrl);
        log.info("TC-UI-ACC-001 PASSED — url: {}", landingUrl);
    }

    @Test(priority = 2)
    @TestCaseId({
            "TC-UI-ACC-002",
            "TC-ACC-002"
    })
    @Story("Accountant cabinet — logout")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Бухгалтер (accountant) входить через форму Keycloak, відкриває меню користувача
            у sidebar і натискає «Вийти». Перевіряється повернення на сторінку логіну Keycloak.
            """)
    public void accountantLogoutTest() {
        performLogout();
    }

    @Test(priority = 3)
    @TestCaseId("TC-UI-ACC-003")
    @Story("Accountant workspace without UNIT")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Після логіну accountant: my-units API не містить UNIT-локацій")
    public void accountantWorkspaceExcludesUnitLocations() {
        loginAsAccountant();

        var apiResponse = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_MY_UNITS, UserRole.ACCOUNTANT);
        assertThat(apiResponse.statusCode()).isEqualTo(200);
        List<StorageResponse> units = DatabaseIntegrityValidator.extractList(apiResponse, StorageResponse.class);
        assertThat(units.stream().filter(s -> "UNIT".equalsIgnoreCase(s.getType())).toList())
                .as("API my-units для accountant не повинен містити UNIT")
                .isEmpty();
    }

    @Test(priority = 4)
    @TestCaseId("TC-UI-ACC-004")
    @Story("Accountant logistics filter")
    @Severity(SeverityLevel.NORMAL)
    @Description("Fixture UNIT→CREW через API; /logistics не показує crew у журналі")
    public void accountantLogisticsHidesUnitToCrew() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("acc-ui-crew-");
        relocationFixture.ensureStock(scenario.unit().getId(), resourceId, 50.0);

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.unit().getId(),
                scenario.crew().getId(),
                resourceId,
                SEND_AMOUNT);

        loginAsAccountant();
        injectAccountantStorage(owner1StorageId);

        RelocationPage logistics = new RelocationPage(page).openLogistics().openSentTab();
        Set<String> journalText = logistics.getDisplayedJournalRows().stream()
                .map(row -> (row.getSenderName() != null ? row.getSenderName() : "")
                        + (row.getRecipientName() != null ? row.getRecipientName() : ""))
                .collect(Collectors.toSet());

        String crewMarker = scenario.crew().getName();
        assertThat(journalText.stream().noneMatch(text -> text.contains(crewMarker)))
                .as("UNIT→CREW не повинен з'являтися в логістиці accountant")
                .isTrue();
    }

    private void loginAsAccountant() {
        browserContext.clearCookies();
        browserContext.addInitScript("localStorage.clear();");
        String username = UserRole.ACCOUNTANT.getUsername();
        String password = UserRole.ACCOUNTANT.getPassword();
        String frontendUrl = ConfigProvider.getBaseUrl();
        String backendUrl = ConfigProvider.getBackendUrl();
        LoginPage loginPage = new LoginPage(page);
        loginPage.open(backendUrl, frontendUrl + POST_LOGIN_PATH);
        loginPage.login(username, password, POST_LOGIN_PATH);
        new ProductionPage(page).waitForLoaded();
    }

    private void injectAccountantStorage(long storageId) {
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        page.reload();
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
