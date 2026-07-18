package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.GlobalPlansPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI Smoke")
public class GlobalPlansUiTest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());

        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];

        injectSessionCookies(cookies, domain);
        log.info("ADMIN session injected for Global Plans UI smoke — domain: {}", domain);
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-UI-SMOKE-001")
    @Story("Admin opens global plans list")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** smoke — адміністратор з валідною сесією відкриває список глобальних планів.
            
            **Маршрут:** `/global-plans` (sidebar «Виробничі плани» → PageTab «Глобальні плани»)
            **Роль:** ADMIN (cookies через Playwright inject)
            
            **Параметри:** без попереднього API-setup; лише browser session.
            
            **Перевірки:**
            - PageTab / CTA «Глобальні плани» видимий.
            - Скріншот прикріплено до Allure.
            """)
    public void adminOpensGlobalPlansList() {
        GlobalPlansPage listPage = new GlobalPlansPage(page).open();
        listPage.attachScreenshot("TC-GP-UI-SMOKE-001 — global plans list");

        assertThat(listPage.isListHeadingVisible())
                .as("PageTab / екран «Глобальні плани» має бути видимим")
                .isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-UI-SMOKE-002")
    @Story("Admin opens create wizard")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** smoke — перехід зі списку у wizard створення глобального плану.
            
            **Маршрути:** `/global-plans` → кнопка створення → wizard
            **Роль:** ADMIN
            
            **Параметри:** нова сесія на списку, клік «Новий Глобальний план».
            
            **Перевірки:**
            - Заголовок wizard «Декомпозиція виробничого плану» видимий.
            - Скріншот wizard у звіті.
            """)
    public void adminOpensCreateWizard() {
        GlobalPlansPage listPage = new GlobalPlansPage(page).open();
        GlobalPlanWizardPage wizard = listPage.clickCreatePlan();
        wizard.attachScreenshot("TC-GP-UI-SMOKE-002 — wizard");

        assertThat(wizard.isWizardHeadingVisible())
                .as("Заголовок wizard «Декомпозиція виробничого плану» має бути видимим")
                .isTrue();
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-UI-SMOKE-003")
    @Story("Wizard tabs state on fresh create")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** на новому wizard відображаються 4 кроки декомпозиції; пізні вкладки заблоковані до завершення розподілу.
            
            **Маршрут:** `/global-plans` → wizard створення
            **Роль:** ADMIN
            
            **Очікувані вкладки:**
            1. (перший крок — output/період)
            2. «Хто буде виробляти?»
            3. «Потрібно ресурсів»
            4. «Плани на локації»
            
            **Перевірки:**
            - Усі 4 вкладки видимі.
            - Вкладки 3 і 4 disabled на свіжому створенні (`areLateTabsDisabledOnFreshCreate`).
            """)
    public void freshCreateShowsFourTabsWithLateTabsDisabled() {
        GlobalPlansPage listPage = new GlobalPlansPage(page).open();
        listPage.attachScreenshot("TC-GP-UI-SMOKE-003 — global plans list");

        GlobalPlanWizardPage wizard = listPage.clickCreatePlan();
        wizard.attachScreenshot("TC-GP-UI-SMOKE-003 — wizard initial");

        log.info("TC-GP-UI-SMOKE-003: verifying 4 tabs and late tabs disabled on fresh create");

        assertThat(wizard.isFirstTabVisible()).isTrue();
        assertThat(wizard.isTabVisible("2. Хто буде виробляти?")).isTrue();
        assertThat(wizard.isTabVisible("3. Потрібно ресурсів")).isTrue();
        assertThat(wizard.isTabVisible("4. Плани на локації")).isTrue();
        assertThat(wizard.areLateTabsDisabledOnFreshCreate())
                .as("Вкладки 3 і 4 мають бути заблоковані до завершення розподілу")
                .isTrue();

        wizard.attachScreenshot("TC-GP-UI-SMOKE-003 — tabs verified");
    }
}
