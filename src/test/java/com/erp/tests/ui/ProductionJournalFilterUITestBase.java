package com.erp.tests.ui;

import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionJournalFilterCatalog;
import com.erp.utils.helpers.ProductionJournalUiVerification;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
abstract class ProductionJournalFilterUITestBase extends BaseUITest {

    protected ProductionFixture productionFixture;
    protected ProductionJournalFilterCatalog catalog;
    protected long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        catalog = ProductionJournalFilterCatalog.load(productionFixture, storageId);

        log.info("Injecting OWNER_1 session for production journal filter UI tests");
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
    }

    /**
     * Full UI flow: open journal, apply filters with visibility checks, attach screenshots,
     * then cross-verify table rows against API.
     */
    protected void runFilterUiScenario(String testCaseId, ProductionJournalFilterScenario scenario) {
        attachScenarioParameters(scenario);

        ProductionPage productionPage = Allure.step("Відкрити журнал виробництва (/production)", () -> {
            ProductionPage journal = new ProductionPage(page).open();
            assertJournalControlsVisible(journal);
            journal.attachScreenshot(testCaseId + " — journal initial");
            return journal;
        });

        Allure.step("Очистити фільтри", () -> {
            productionPage.clearFilters();
            productionPage.attachScreenshot(testCaseId + " — filters cleared");
        });

        Allure.step("Застосувати фільтри на UI: «" + scenario.name() + "»", () -> {
            applyFiltersOnUi(productionPage, scenario);
            assertFiltersReflectedOnUi(productionPage, scenario);
            productionPage.attachScreenshot(testCaseId + " — filters applied");
        });

        Allure.step("Перевірити таблицю журналу проти API", () -> {
            ProductionJournalUiVerification.assertJournalMatchesApi(
                    productionPage,
                    productionFixture,
                    scenario.query(),
                    scenario.anchor(),
                    "UI-таблиця має збігатися з GET /productions для фільтра «" + scenario.name() + "»");
            productionPage.attachScreenshot(testCaseId + " — verification passed");
        });
    }

    @Step("Перевірити наявність елементів журналу виробництва")
    private void assertJournalControlsVisible(ProductionPage productionPage) {
        assertThat(productionPage.isManufacturingButtonVisible())
                .as("Кнопка «Виготовлення» має бути видимою")
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
                .as("Таблиця журналу має бути видимою")
                .isTrue();
        assertThat(productionPage.isJournalLoadErrorVisible())
                .as("Помилка завантаження журналу не повинна відображатися")
                .isFalse();
    }

    private void applyFiltersOnUi(ProductionPage productionPage,
                                  ProductionJournalFilterScenario scenario) {
        ProductionPage.ProductionJournalFilterState filterState =
                new ProductionPage.ProductionJournalFilterState(
                        scenario.productTerm(),
                        scenario.categoryName(),
                        scenario.startDate(),
                        scenario.endDate());
        productionPage.applyFilters(filterState);
    }

    private void assertFiltersReflectedOnUi(ProductionPage productionPage,
                                            ProductionJournalFilterScenario scenario) {
        if (scenario.productTerm() != null) {
            assertThat(productionPage.getProductFilterValue())
                    .as("Значення поля «Продукт»")
                    .contains(scenario.productTerm());
        }
        if (scenario.startDate() != null) {
            assertThat(productionPage.getDateFromValue())
                    .as("Дата «З»")
                    .isEqualTo(scenario.startDate().toString());
        }
        if (scenario.endDate() != null) {
            assertThat(productionPage.getDateToValue())
                    .as("Дата «По»")
                    .isEqualTo(scenario.endDate().toString());
        }
        if (scenario.categoryName() != null) {
            assertThat(productionPage.getSelectedCategoryLabel())
                    .as("Обрана категорія")
                    .contains(scenario.categoryName());
        }

        assertThat(productionPage.isJournalLoadErrorVisible())
                .as("Після застосування фільтрів не повинно бути помилки завантаження")
                .isFalse();
        assertThat(productionPage.isProductionTableVisible())
                .as("Таблиця журналу має залишатися видимою після фільтрації")
                .isTrue();
    }

    protected ProductionJournalFilterScenario requireCategoryScenario(
            Optional<ProductionJournalFilterScenario> scenario) {
        return scenario.orElseThrow(() ->
                new SkipException("Немає записів виробництва з відомою категорією для UI-тесту фільтра"));
    }

    private void attachScenarioParameters(ProductionJournalFilterScenario scenario) {
        Allure.parameter("filter", scenario.name());
        Allure.parameter("anchorProductionId", scenario.anchor().getId());
        if (scenario.productTerm() != null) {
            Allure.parameter("productTerm", scenario.productTerm());
        }
        if (scenario.startDate() != null) {
            Allure.parameter("startDate", scenario.startDate());
        }
        if (scenario.endDate() != null) {
            Allure.parameter("endDate", scenario.endDate());
        }
        if (scenario.categoryId() != null) {
            Allure.parameter("categoryId", scenario.categoryId());
            Allure.parameter("categoryName", scenario.categoryName());
        }
    }
}
