package com.erp.tests.ui;

import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.HashtagTestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;

import java.util.Map;

@Slf4j
abstract class ProductionHashtagUITestBase extends BaseUITest {

    protected static final double MIN_INPUT_STOCK = 500.0;
    protected static final double PRODUCTION_AMOUNT = 1.0;

    protected ProductionFixture productionFixture;
    protected TechnologicalMapFixture techMapFixture;
    protected long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();

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

    @Step("Відкрити журнал виробництва з очищеними фільтрами")
    protected ProductionPage openProductionJournal() {
        ProductionPage journal = new ProductionPage(page).open();
        journal.clearFilters();
        return journal;
    }

    protected TechnologicalMapFixture.IsolatedTechMapContext prepareIsolatedTechMap() {
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.seedStockForIsolatedTechMap(
                productionFixture, storageId, isolated.getTechMap(), MIN_INPUT_STOCK);
        return isolated;
    }

    protected ManufacturingItemResponse createProduction(TechnologicalMapFixture.IsolatedTechMapContext isolated) {
        return productionFixture.createAs(
                UserRole.OWNER_1,
                storageId,
                isolated.getTechMap(),
                PRODUCTION_AMOUNT,
                ProductionDataFactory.uniqueBatchNumber());
    }

    protected String uniqueUiTag(String prefix) {
        return HashtagTestData.uniqueTag(prefix);
    }
}
