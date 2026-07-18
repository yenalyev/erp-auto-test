package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionJournalUiVerification;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke tests for the Production Journal page.
 *
 * TC-UI-PROD-001 — OWNER_1 opens the production journal, verifies key UI sections
 * and that production records are displayed in the table.
 */
@Slf4j
@Epic("Production")
@Feature("Production Journal UI")
public class ProductionUITest extends BaseUITest {

    private ProductionFixture productionFixture;
    private long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();

        log.info("Injecting OWNER_1 session cookies into BrowserContext");

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());

        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];

        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');"
                        + "localStorage.setItem('" + ProductionPage.pageSizeStorageKey() + "', '"
                        + ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE + "');");
        log.info("OWNER_1 session injected — domain: {}, storageId: {}", domain, storageId);
    }

    @Test
    @TestCaseId("TC-UI-PROD-001")
    @Story("Production Journal — page structure and data smoke")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після логіну OWNER_1 відкриває сторінку журналу виробництва (/production).
            Перевіряється наявність:
            — кнопок «Виготовлення» та «Розбір»
            — фільтрів: поле «Продукт», «Період» (DateRangePicker), кнопка «Очистити»
            — таблиці з записами виробництва (не порожній стан «Нічого не знайдено»)
            — відповідність відображених рядків останнім записам з API (перша сторінка, сортування за датою)
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

        assertThat(productionPage.isPeriodFilterVisible())
                .as("Фільтр «Період» має бути видимим")
                .isTrue();

        assertThat(productionPage.isClearButtonVisible())
                .as("Кнопка «Очистити» має бути видимою")
                .isTrue();

        assertThat(productionPage.isProductionTableVisible())
                .as("Контейнер таблиці виробництва має бути видимим")
                .isTrue();

        assertThat(productionPage.isJournalLoadErrorVisible())
                .as("Помилка завантаження журналу виробництва не повинна відображатися")
                .isFalse();

        assertThat(productionPage.isEmptyStateVisible())
                .as("Порожній стан «Нічого не знайдено» не повинен відображатися")
                .isFalse();

        assertThat(productionPage.hasProductionRecords())
                .as("Журнал виробництва має містити принаймні один запис у таблиці")
                .isTrue();

        assertThat(productionPage.getProductionRecordCount())
                .as("Кількість рядків у таблиці виробництва")
                .isGreaterThan(0);

        Allure.step("Перевірити опорний запис журналу проти API (перша сторінка)", () -> {
            int pageSize = productionPage.getSelectedPageSize();
            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .pageSize(pageSize)
                    .build();

            List<ManufacturingItemResponse> apiPage = productionFixture.getJournalPage(query);
            assertThat(apiPage)
                    .as("API має повернути принаймні один запис виробництва для storageId=%s", storageId)
                    .isNotEmpty();

            ManufacturingItemResponse anchor = apiPage.getFirst();
            ProductionJournalUiVerification.assertJournalMatchesApi(
                    productionPage,
                    productionFixture,
                    query,
                    anchor,
                    "Опорний запис (перший на сторінці API) має відображатися на UI");

            Allure.parameter("storageId", storageId);
            Allure.parameter("pageSize", pageSize);
            Allure.parameter("anchorProductionId", anchor.getId());
            Allure.parameter("anchorBatchNumber", anchor.getBatchNumber());
            if (anchor.getProduct() != null) {
                Allure.parameter("anchorProduct", anchor.getProduct().getName());
            }
        });

        productionPage.attachScreenshot("Production Journal — all assertions passed");

        Allure.parameter("User", UserRole.OWNER_1.getUsername());
        Allure.parameter("URL", productionPage.currentUrl());

        log.info("TC-UI-PROD-001 PASSED — url: {}", productionPage.currentUrl());
    }
}
