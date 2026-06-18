package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
        long storageId = ConfigProvider.getOwner1StorageId();
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("OWNER_1 session injected — domain: {}, storageId: {}", domain, storageId);
    }

    @Test
    @TestCaseId("TC-UI-PROD-001")
    @Story("Production Journal — page structure smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після логіну OWNER_1 відкриває сторінку журналу виробництва (/production).
            Перевіряється наявність:
            — кнопок «Виготовлення» та «Розбір»
            — фільтрів: поле «Продукт», датапікери «З» і «По», кнопка «Очистити»
            — таблиці з виробництвом
            Додатково робиться скріншот сторінки.
            """)
    public void productionJournalPageSmokeTest() {
        log.info("TC-UI-PROD-001: Opening Production Journal");

        ProductionPage productionPage = new ProductionPage(page);
        productionPage.open();

        productionPage.attachScreenshot("Production Journal — initial load");

        assertThat(productionPage.isManufacturingButtonVisible())
                .as("Кнопка «Виготовлення» має бути видимою")
                .isTrue();

        assertThat(productionPage.isDisassembleButtonVisible())
                .as("Кнопка «Розбір» має бути видимою")
                .isTrue();

        assertThat(productionPage.isProductFilterVisible())
                .as("Поле фільтру «Продукт» має бути видимим")
                .isTrue();

        assertThat(productionPage.isDateFromVisible())
                .as("Датапікер «З» має бути видимим")
                .isTrue();

        assertThat(productionPage.isDateToVisible())
                .as("Датапікер «По» має бути видимим")
                .isTrue();

        assertThat(productionPage.isClearButtonVisible())
                .as("Кнопка «Очистити» має бути видимою")
                .isTrue();

        assertThat(productionPage.isProductionTableVisible())
                .as("Таблиця виробництва має бути видимою")
                .isTrue();

        productionPage.attachScreenshot("Production Journal — all assertions passed");

        Allure.parameter("User", UserRole.OWNER_1.getUsername());
        Allure.parameter("URL", productionPage.currentUrl());

        log.info("TC-UI-PROD-001 PASSED — url: {}", productionPage.currentUrl());
    }
}
