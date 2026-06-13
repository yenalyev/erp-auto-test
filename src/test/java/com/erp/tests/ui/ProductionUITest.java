package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke tests for the Production Journal page.
 *
 * TC-UI-PROD-001 — OWNER_1 opens the production journal and verifies all key UI sections.
 */
@Slf4j
@Epic("Production")
@Feature("Production Journal UI")
public class ProductionUITest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        log.info("Injecting OWNER_1 session cookies into BrowserContext");

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());

        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];

        injectSessionCookies(cookies, domain);
        log.info("OWNER_1 session injected — domain: {}", domain);
    }

    @Test
    @TestCaseId("TC-UI-PROD-001")
    @Story("Production Journal — page structure smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після логіну OWNER_1 відкриває сторінку журналу виготовленої продукції.
            Перевіряється наявність:
            — заголовку "Журнал виготовленої продукції"
            — кнопки "Додати"
            — блоку фільтрів: поле "Продукт", датапікери "З" і "По", кнопка "Очистити"
            — блоку з виготовленою продукцією
            Додатково робиться скріншот сторінки.
            """)
    public void productionJournalPageSmokeTest() {
        log.info("TC-UI-PROD-001: Opening Production Journal");

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.open();

        productionPage.attachScreenshot("Production Journal — initial load");

        assertThat(productionPage.isTitleVisible())
                .as("Заголовок 'Журнал виготовленої продукції' має бути видимим")
                .isTrue();

        assertThat(productionPage.isAddButtonVisible())
                .as("Кнопка 'Додати' має бути видимою")
                .isTrue();

        assertThat(productionPage.isFilterBlockVisible())
                .as("Блок фільтрів має бути видимим")
                .isTrue();

        assertThat(productionPage.isProductInputVisible())
                .as("Поле фільтру 'Продукт' (input[placeholder='Пошук...']) має бути видимим")
                .isTrue();

        assertThat(productionPage.isDateFromVisible())
                .as("Датапікер 'З' має бути видимим")
                .isTrue();

        assertThat(productionPage.isDateToVisible())
                .as("Датапікер 'По' має бути видимим")
                .isTrue();

        assertThat(productionPage.isClearButtonVisible())
                .as("Кнопка 'Очистити' має бути видимою")
                .isTrue();

        assertThat(productionPage.isProductionTableVisible())
                .as("Блок з виготовленою продукцією має бути видимим")
                .isTrue();

        productionPage.attachScreenshot("Production Journal — all assertions passed");

        Allure.parameter("User", UserRole.OWNER_1.getUsername());
        Allure.parameter("URL", productionPage.currentUrl());

        log.info("TC-UI-PROD-001 PASSED — url: {}", productionPage.currentUrl());
    }
}
